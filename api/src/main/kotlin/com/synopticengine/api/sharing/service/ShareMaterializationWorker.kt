package com.synopticengine.api.sharing.service

import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.sharing.domain.RelationshipStatus
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.domain.ShareMaterializationOp
import com.synopticengine.api.sharing.domain.ShareMaterializationTask
import com.synopticengine.api.sharing.domain.VisibilitySource
import com.synopticengine.api.sharing.repo.ShareMaterializationTaskRepository
import com.synopticengine.api.sharing.repo.TenantRelationshipRepository
import com.synopticengine.api.sharing.repo.TenantSharePolicyRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Drains [ShareMaterializationTask] rows and updates [com.synopticengine.api.sharing.domain.ResourceVisibility].
 *
 * For Sprint 2b: filter_jsonb evaluation is not implemented — every policy materializes
 * all the source tenant's records of the resource_type. The hook is in place
 * ([matchesFilter]) so a JSON-DSL evaluator can drop in later. virtual visibility
 * (materialize=false) is honoured here: such policies skip the materialization step
 * entirely and rely on the RLS / Hibernate filter to evaluate at query time.
 *
 * P1-1: every task carries its own `tenantId` (the policy's source tenant, captured
 * at enqueue time). [drainQueue] runs on `@Scheduled` so there's no request thread —
 * before each task it sets `TenantContext.runAs(task.tenantId)` so the policy and
 * relationship lookups, the native owner-record SELECT, and the visibility upserts
 * all run with a populated tenant context (and the SET LOCAL GUC that depends on it).
 */
@Component
class ShareMaterializationWorker(
    private val taskRepository: ShareMaterializationTaskRepository,
    private val policyRepository: TenantSharePolicyRepository,
    private val relationshipRepository: TenantRelationshipRepository,
    private val visibilityService: ResourceVisibilityService,
    @PersistenceContext private val em: EntityManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Enqueue a materialization task and execute it synchronously inside the caller's
     * transaction. For low-volume tenants this is the happy path; the [drainQueue]
     * tick handles retry for tasks that fail (e.g. transient DB errors).
     *
     * Must be called inside a populated [TenantContext] — the active tenant id is
     * stamped on the task so [drainQueue] can re-establish it for retries.
     */
    @Transactional
    fun enqueue(
        policyId: UUID,
        op: ShareMaterializationOp,
    ): ShareMaterializationTask {
        val tenantId =
            TenantContext.get()
                ?: error("ShareMaterializationWorker.enqueue called without an active TenantContext")
        val task =
            ShareMaterializationTask().apply {
                this.policyId = policyId
                this.tenantId = tenantId
                this.op = op
            }
        val saved = taskRepository.save(task)
        try {
            runTask(saved)
        } catch (ex: Exception) {
            log.error("Inline materialization for policy $policyId failed; will be retried by drainQueue()", ex)
            markFailed(saved.id!!, ex.message ?: ex::class.java.simpleName)
        }
        return saved
    }

    /**
     * Retry pending / failed tasks. Runs at a long interval — the happy path materializes
     * synchronously in [enqueue]. This loop catches tasks that failed transiently.
     *
     * The `@Scheduled` thread has no TenantContext, so we wrap each task in
     * `TenantContext.runAs(task.tenantId)` to give the inner `@Transactional`
     * method a populated context (otherwise the Hibernate filter would never apply
     * and the RLS GUC would never be set).
     */
    @Scheduled(fixedDelayString = "\${synoptic.sharing.materialization-retry-ms:60000}")
    fun drainQueue() {
        val pending = taskRepository.findPending()
        if (pending.isEmpty()) return
        log.info("Draining ${pending.size} share materialization task(s)")
        for (task in pending) {
            try {
                TenantContext.runAs(task.tenantId) { runTask(task) }
            } catch (ex: Exception) {
                log.error("Failed to materialize task ${task.id} for policy ${task.policyId}", ex)
                markFailed(task.id!!, ex.message ?: ex::class.java.simpleName)
            }
        }
    }

    /**
     * Public for test use — run a single task synchronously. Caller must establish [TenantContext].
     *
     * Wrapped in try-finally: `finishedAt` is **always** set, even when the body throws
     * after `startedAt` was already stamped. Without this guarantee a crashed task
     * would re-enter `drainQueue()`'s "pending" set forever; the unique constraint on
     * `resource_visibility` would then bite on the second attempt and the whole task
     * would roll back partial work. The [enqueue] catch-block and [drainQueue]'s
     * per-task catch are still in place; they preserve the original exception while
     * `markFailed` records the error message after this method has exited.
     */
    @Transactional
    fun runTask(task: ShareMaterializationTask) {
        task.startedAt = Instant.now()
        try {
            val policy = policyRepository.findById(task.policyId).orElse(null)
            if (policy == null) {
                // policy was hard-deleted before the worker got to it — treat as revoke
                visibilityService.deleteBySource(VisibilitySource.POLICY, task.policyId)
                return
            }
            when (task.op) {
                ShareMaterializationOp.INSERT, ShareMaterializationOp.UPDATE -> materializePolicy(policy.id!!)
                ShareMaterializationOp.DELETE, ShareMaterializationOp.REVOKE -> unmaterializePolicy(policy.id!!)
            }
        } finally {
            task.finishedAt = Instant.now()
            taskRepository.save(task)
        }
    }

    private fun materializePolicy(policyId: UUID) {
        val policy = policyRepository.findById(policyId).orElseThrow { NoSuchElementException("Policy gone") }
        if (policy.revokedAt != null) {
            visibilityService.deleteBySource(VisibilitySource.POLICY, policyId)
            return
        }
        if (!policy.materialize) {
            // Virtual visibility; nothing to populate. The RLS path evaluates via policy lookup at query time.
            return
        }
        val rel =
            relationshipRepository.findById(policy.relationshipId).orElseThrow {
                NoSuchElementException("Relationship gone")
            }
        if (rel.status != RelationshipStatus.ACTIVE) {
            // Don't expose anything until the relationship is accepted.
            visibilityService.deleteBySource(VisibilitySource.POLICY, policyId)
            return
        }
        val resourceType =
            try {
                ResourceType.fromLiteral(policy.resourceType)
            } catch (ex: IllegalArgumentException) {
                log.warn("Skipping policy $policyId — unknown resource type: ${policy.resourceType}")
                return
            }
        val table = tableForResource(resourceType) ?: return
        val ownerTenantId = rel.sourceTenantId
        val consumerTenantId = rel.targetTenantId

        // Walk every active record in the owner tenant. The TenantContext is set to
        // ownerTenantId (we enqueue from the source tenant; drainQueue restores it),
        // so the RLS GUC matches and the native query returns the owner's rows.
        @Suppress("UNCHECKED_CAST")
        val ids =
            em
                .createNativeQuery(
                    "SELECT id FROM $table WHERE tenant_id = :tenant AND deleted_at IS NULL",
                ).setParameter("tenant", ownerTenantId)
                .resultList as List<Any>
        ids
            .asSequence()
            .map { (it as java.util.UUID) }
            .filter { matchesFilter(policy.filterJson, it) }
            .forEach { resourceId ->
                visibilityService.upsert(
                    ownerTenantId = ownerTenantId,
                    consumerTenantId = consumerTenantId,
                    resourceType = resourceType.literal,
                    resourceId = resourceId,
                    accessLevel = policy.accessLevel,
                    source = VisibilitySource.POLICY,
                    sourceId = policyId,
                )
            }
        log.info(
            "Policy $policyId materialized ${ids.size} ${resourceType.literal} row(s) for consumer $consumerTenantId",
        )
    }

    private fun unmaterializePolicy(policyId: UUID) {
        val n = visibilityService.deleteBySource(VisibilitySource.POLICY, policyId)
        log.info("Policy $policyId unmaterialized: removed $n visibility row(s)")
    }

    /**
     * Maps a [ResourceType] to its physical table. Returns null when no table is wired
     * yet (e.g. activities — they're shared via cascade, not policy, in Sprint 2c).
     */
    private fun tableForResource(rt: ResourceType): String? =
        when (rt) {
            ResourceType.LEADS -> "leads"
            ResourceType.PERSONS -> "persons"
            ResourceType.ORGANIZATIONS -> "organizations"
            ResourceType.PRODUCTS -> "products"
            ResourceType.QUOTES -> "quotes"
            ResourceType.WAREHOUSES -> "warehouses"
            ResourceType.ACTIVITIES, ResourceType.PRICELISTS -> null
        }

    /**
     * Placeholder for policy filter_jsonb evaluation. Sprint 2b ships with "match all".
     * Sprint 2c adds a JSON-DSL evaluator (operator+attribute+value AST).
     *
     * `TenantSharePolicyService` rejects non-null filterJson at create/update time, so this
     * function is only ever called with `filterJson = null` against fresh policies. If a
     * legacy row from an earlier build still has a filter, we materialize as if the filter
     * matched everything (the docstring's "match all" intent) rather than the previous
     * "match nothing" behaviour, which silently broke filtered policies.
     */
    private fun matchesFilter(
        @Suppress("UNUSED_PARAMETER") filterJson: String?,
        @Suppress("UNUSED_PARAMETER") resourceId: java.util.UUID,
    ): Boolean = true

    @Transactional
    fun markFailed(
        taskId: UUID,
        message: String,
    ) {
        val task = taskRepository.findById(taskId).orElse(null) ?: return
        task.error = message
        task.finishedAt = Instant.now()
        taskRepository.save(task)
    }
}
