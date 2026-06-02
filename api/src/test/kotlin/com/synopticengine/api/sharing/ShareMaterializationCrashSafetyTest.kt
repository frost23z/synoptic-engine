package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.sharing.domain.ShareMaterializationOp
import com.synopticengine.api.sharing.domain.ShareMaterializationTask
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import com.synopticengine.api.sharing.repo.ShareMaterializationTaskRepository
import com.synopticengine.api.sharing.service.ShareMaterializationWorker
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T7.2a — Materialization crash-safety and idempotency.
 *
 * Tests two invariants guaranteed by [ShareMaterializationWorker.runTask]:
 *
 * 1. **finishedAt is always set** — the `try-finally` in `runTask` stamps
 *    `finishedAt` in the same transaction as the work. If the task body throws,
 *    the `@Scheduled` drainQueue's catch block calls [ShareMaterializationWorker.markFailed]
 *    which sets `finishedAt` in a separate transaction. Neither path leaves the
 *    task in a perpetually-pending state.
 *
 * 2. **No orphan/dup on retry** — calling `runTask` twice for the same policy
 *    must produce the same `resource_visibility` rows as calling it once
 *    (idempotent upsert in [com.synopticengine.api.sharing.service.ResourceVisibilityService]).
 */
class ShareMaterializationCrashSafetyTest : AbstractIntegrationTest() {
    @Autowired private lateinit var worker: ShareMaterializationWorker

    @Autowired private lateinit var taskRepository: ShareMaterializationTaskRepository

    @Autowired private lateinit var visibilityRepository: ResourceVisibilityRepository

    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    // ── finishedAt is always set ──────────────────────────────────────────────

    @Test
    fun `runTask - finishedAt is set even when policy does not exist (null-policy path)`() {
        val owner = tenantProvisioner.provision("crashsafe-null")

        // Persist a task whose policyId points at a non-existent policy.
        // runTask handles this gracefully (calls deleteBySource and returns).
        val task =
            TransactionTemplate(transactionManager).execute {
                taskRepository.save(
                    ShareMaterializationTask().apply {
                        policyId = UUID.randomUUID() // deliberate missing policy
                        tenantId = owner.tenantId
                        op = ShareMaterializationOp.INSERT
                    },
                )
            }

        assertNull(task.finishedAt, "Task should have null finishedAt before running")

        TenantContext.runAs(owner.tenantId) { worker.runTask(task) }

        val reloaded = taskRepository.findById(task.id!!).orElseThrow()
        assertNotNull(reloaded.finishedAt, "finishedAt must be set after runTask (null-policy path)")
    }

    @Test
    fun `runTask - finishedAt is set on a successful materialization`() {
        val owner = tenantProvisioner.provision("crashsafe-ok-o")
        val consumer = tenantProvisioner.provision("crashsafe-ok-c")

        leadFactory.create(owner.token)

        val relId = acceptedRelationship(owner, consumer, "PARTNER")
        val policyId = createPolicy(relId, owner.token, "leads", "READ")

        // The inline enqueue already ran runTask once; fetch the task it created.
        val tasks = taskRepository.findAll().filter { it.policyId == UUID.fromString(policyId) }
        assertTrue(tasks.isNotEmpty(), "Expected at least one task for the created policy")
        val task = tasks.first()

        assertNotNull(task.finishedAt, "finishedAt must be set after successful materialization")
    }

    // ── No orphan/dup on retry ────────────────────────────────────────────────

    @Test
    fun `running materialization twice produces the same resource_visibility rows (idempotent)`() {
        val owner = tenantProvisioner.provision("crashsafe-idem-o")
        val consumer = tenantProvisioner.provision("crashsafe-idem-c")

        leadFactory.create(owner.token)

        val relId = acceptedRelationship(owner, consumer, "PARTNER")
        val policyId = UUID.fromString(createPolicy(relId, owner.token, "leads", "READ"))

        val visibilityAfterFirst =
            visibilityRepository
                .findAll()
                .filter { it.sourceId == policyId }
                .sortedBy { it.resourceId.toString() }

        assertTrue(visibilityAfterFirst.isNotEmpty(), "Expected visibility rows after first materialization")

        // Second run: create a new task for the same policy and run it.
        val retryTask =
            TransactionTemplate(transactionManager).execute {
                taskRepository.save(
                    ShareMaterializationTask().apply {
                        this.policyId = policyId
                        this.tenantId = owner.tenantId
                        this.op = ShareMaterializationOp.UPDATE
                    },
                )
            }

        TenantContext.runAs(owner.tenantId) { worker.runTask(retryTask) }

        val visibilityAfterSecond =
            visibilityRepository
                .findAll()
                .filter { it.sourceId == policyId }
                .sortedBy { it.resourceId.toString() }

        // Same count and same resource IDs — no duplicates.
        assertEquals(
            visibilityAfterFirst.size,
            visibilityAfterSecond.size,
            "Retry must not create duplicate visibility rows",
        )
        val firstIds = visibilityAfterFirst.map { it.resourceId }.toSet()
        val secondIds = visibilityAfterSecond.map { it.resourceId }.toSet()
        assertEquals(firstIds, secondIds, "Resource IDs must be identical after retry")
    }

    @Test
    fun `markFailed sets finishedAt in a separate transaction (simulates drainQueue crash recovery)`() {
        val owner = tenantProvisioner.provision("crashsafe-mf")

        val task =
            TransactionTemplate(transactionManager).execute {
                taskRepository.save(
                    ShareMaterializationTask().apply {
                        policyId = UUID.randomUUID()
                        tenantId = owner.tenantId
                        op = ShareMaterializationOp.INSERT
                    },
                )
            }

        assertNull(task.finishedAt)

        worker.markFailed(task.id!!, "simulated crash")

        val reloaded = taskRepository.findById(task.id!!).orElseThrow()
        assertNotNull(reloaded.finishedAt, "markFailed must stamp finishedAt")
        assertEquals("simulated crash", reloaded.error)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun acceptedRelationship(
        source: TenantProvisioner.TenantAndToken,
        target: TenantProvisioner.TenantAndToken,
        type: String,
    ): String {
        val relId =
            post(
                "/api/relationships",
                source.token,
                mapOf("targetTenantId" to target.tenantId.toString(), "type" to type),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", target.token)
        return relId
    }

    private fun createPolicy(
        relId: String,
        ownerToken: String,
        resourceType: String,
        accessLevel: String,
    ): String {
        val resp =
            post(
                "/api/relationships/$relId/policies",
                ownerToken,
                mapOf("resourceType" to resourceType, "accessLevel" to accessLevel, "materialize" to true),
            )
        assertEquals(201, resp.status(), resp.response.contentAsString)
        return resp.bodyAsMap()!!["id"] as String
    }
}
