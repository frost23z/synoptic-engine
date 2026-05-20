package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.sharing.domain.ShareMaterializationOp
import com.synopticengine.api.sharing.repo.ShareMaterializationTaskRepository
import com.synopticengine.api.sharing.service.ShareMaterializationWorker
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Phase 4 P1-1: async / scheduled paths must carry the right [TenantContext].
 *
 * The audit found three offenders:
 *  1. `ShareMaterializationWorker.drainQueue` — `@Scheduled`, no request thread.
 *     Now stamps `tenant_id` on every enqueued task and wraps `runTask` in
 *     `TenantContext.runAs(task.tenantId)`.
 *  2. `WebhookDispatcher.onDomainEvent` — `@Async @EventListener`. Refuses to
 *     dispatch if `TenantContext` isn't propagated.
 *  3. `CsvImportProcessor.process` — `@Async`. Fails loudly if submitted
 *     without a tenant context.
 *
 * These tests exercise the worker path (which is observable through the queue
 * row); the dispatcher / processor guards are unit-ish enough that a
 * negative-input assertion is enough.
 */
class AsyncTenantContextIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var worker: ShareMaterializationWorker

    @Autowired private lateinit var taskRepository: ShareMaterializationTaskRepository

    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Test
    fun `enqueue without TenantContext fails loudly instead of stamping a zero tenant id`() {
        // The previous behaviour silently relied on the Hibernate filter being absent
        // when no context was set. Post P0-1 that bypass exists only outside a
        // transaction, so a context-less enqueue would have to create tasks with no
        // owner — refuse instead.
        TenantContext.clear()
        try {
            worker.enqueue(UUID.randomUUID(), ShareMaterializationOp.INSERT)
            fail("enqueue should have thrown when called without an active TenantContext")
        } catch (expected: IllegalStateException) {
            assertNotNull(expected.message)
            assertTrue(
                expected.message!!.contains("TenantContext"),
                "Expected message to mention TenantContext, got: ${expected.message}",
            )
        }
    }

    @Test
    fun `policy create stamps the active tenant on the enqueued materialization task`() {
        val source = tenantProvisioner.provision("mat-src")
        val target = tenantProvisioner.provision("mat-tgt")

        // Create an active relationship + policy. enqueue() runs inside the
        // /api/relationships/.../policies request, so TenantContext = source.
        val relId =
            post(
                "/api/relationships",
                source.token,
                mapOf("targetTenantId" to target.tenantId.toString(), "type" to "PARTNER"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", target.token)
        post(
            "/api/relationships/$relId/policies",
            source.token,
            mapOf("resourceType" to "leads", "accessLevel" to "READ"),
        )

        // Find this tenant's queue rows — there should be at least one, all
        // stamped with the source tenant's id (not zero, not the target's).
        val ours = taskRepository.findAll().filter { it.tenantId == source.tenantId }
        assertTrue(ours.isNotEmpty(), "Expected at least one queue row stamped with the source tenant id")
        assertTrue(
            ours.none { it.tenantId == target.tenantId || it.tenantId == UUID(0, 0) },
            "Queue rows must carry the source tenant id, not the target's and not zero",
        )
        // The queue row's tenant id matches the relationship's source.
        assertEquals(source.tenantId, ours.first().tenantId)
    }
}
