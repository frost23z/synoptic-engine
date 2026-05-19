package com.synopticengine.api.sharing.service

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
     */
    @Transactional
    fun enqueue(
        policyId: UUID,
        op: ShareMaterializationOp,
    ): ShareMaterializationTask {
        val task =
            ShareMaterializationTask().apply {
                this.policyId = policyId
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
     */
    @Scheduled(fixedDelayString = "\${synoptic.sharing.materialization-retry-ms:60000}")
    fun drainQueue() {
        val pending = taskRepository.findPending()
        if (pending.isEmpty()) return
        log.info("Draining ${pending.size} share materialization task(s)")
        for (task in pending) {
            try {
                runTask(task)
            } catch (ex: Exception) {
                log.error("Failed to materialize task ${task.id} for policy ${task.policyId}", ex)
                markFailed(task.id!!, ex.message ?: ex::class.java.simpleName)
            }
        }
    }

    /** Public for test use — run a single task synchronously. */
    @Transactional
    fun runTask(task: ShareMaterializationTask) {
        task.startedAt = Instant.now()
        val policy = policyRepository.findById(task.policyId).orElse(null)
        if (policy == null) {
            // policy was hard-deleted before the worker got to it — treat as revoke
            visibilityService.deleteBySource(VisibilitySource.POLICY, task.policyId)
            task.finishedAt = Instant.now()
            taskRepository.save(task)
            return
        }
        when (task.op) {
            ShareMaterializationOp.INSERT, ShareMaterializationOp.UPDATE -> materializePolicy(policy.id!!)
            ShareMaterializationOp.DELETE, ShareMaterializationOp.REVOKE -> unmaterializePolicy(policy.id!!)
        }
        task.finishedAt = Instant.now()
        taskRepository.save(task)
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

        // Find every active record in the owner tenant. Native query bypasses Hibernate
        // filter (which scopes to current tenant); we want to walk the OWNER's records.
        // RLS sees `tenant_id = ownerTenantId` and permits it via the GUC check; the
        // owner of the policy is the source tenant, which is also the active tenant when
        // the worker is dispatched as part of the policy-creation transaction.
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
     */
    private fun matchesFilter(
        filterJson: String?,
        @Suppress("UNUSED_PARAMETER") resourceId: java.util.UUID,
    ): Boolean = filterJson == null

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
