package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.shared.ActorContext
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.sharing.domain.CrossTenantAction
import com.synopticengine.api.sharing.repo.CrossTenantAuditRepository
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 4 P0-2: every cross-tenant mutation must land a row in `cross_tenant_audit`.
 *
 * Pre-fix, `CrossTenantAuditService.record` was wired through `SharingApi.recordAudit`
 * but had zero call sites. The Phase 4 fix wires
 * [com.synopticengine.api.sharing.CrossTenantWriteInterceptor] (Hibernate
 * `onFlushDirty` on every UPDATE) →
 * [com.synopticengine.api.sharing.events.SharedRecordEditedEvent] →
 * [com.synopticengine.api.sharing.events.CrossTenantAuditEventListener] →
 * `CrossTenantAuditService.record(..., EDIT)`.
 *
 * The test bypasses both the HTTP layer and `LeadService.create` because that
 * service publishes a `lead.created` event that triggers an async `add_tag`
 * workflow action — which races with the test's `saveAndFlush` and produces
 * flaky `OptimisticLockingException`s unrelated to the audit log. Going
 * straight through the repository for both lead creation and the cross-tenant
 * edit gives the test a deterministic shape: a single UPDATE, fired in the
 * consumer's `TenantContext`, that the interceptor sees and turns into one
 * audit row.
 */
class CrossTenantAuditIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadRepository: LeadRepository

    @Autowired private lateinit var stageRepository: StageRepository

    @Autowired private lateinit var auditRepository: CrossTenantAuditRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `cross-tenant edit of a shared lead writes an EDIT row to cross_tenant_audit`() {
        val owner = tenantProvisioner.provision("audit-owner")
        val consumer = tenantProvisioner.provision("audit-consumer")

        val consumerUserId = UUID.fromString(get("/auth/me", consumer.token).bodyAsMap()!!["id"] as String)
        val leadId = createLeadInTenant(owner.tenantId, "Cross-tenant audit probe")
        val relId =
            post(
                "/api/relationships",
                owner.token,
                mapOf("targetTenantId" to consumer.tenantId.toString(), "type" to "PARTNER"),
            ).bodyAsMap()!!["id"] as String
        patch("/api/relationships/$relId/accept", consumer.token)
        assertEquals(
            201,
            post(
                "/api/relationships/$relId/policies",
                owner.token,
                mapOf("resourceType" to "leads", "accessLevel" to "WRITE"),
            ).status(),
        )

        // Cross-tenant edit: the consumer modifies the owner's lead.
        TransactionTemplate(transactionManager).execute {
            TenantContext.runAs(owner.tenantId) {
                ActorContext.runAs(consumerUserId) {
                    val lead =
                        leadRepository.findActiveById(leadId)
                            ?: error("Lead not found in owner tenant scope")
                    // Switch the runtime tenant to the consumer's. The interceptor
                    // uses this to detect cross-tenant writes.
                    TenantContext.runAs(consumer.tenantId) {
                        lead.title = "Edited by consumer"
                        leadRepository.saveAndFlush(lead)
                    }
                }
            }
        }

        val rows = auditRepository.findAll().filter { it.resourceId == leadId }
        assertEquals(1, rows.size, "Expected exactly one audit row for the cross-tenant edit, got ${rows.size}")
        val row = rows.first()
        assertEquals(CrossTenantAction.EDIT, row.action)
        assertEquals(owner.tenantId, row.ownerTenantId)
        assertEquals(consumer.tenantId, row.actorTenantId)
        assertEquals(consumerUserId, row.actorUserId)
        assertEquals("leads", row.resourceType)
        assertEquals(leadId, row.resourceId)
        assertNotNull(row.at)
    }

    @Test
    fun `same-tenant edit on a shareable entity does not write to cross_tenant_audit`() {
        val owner = tenantProvisioner.provision("audit-same")
        val ownerUserId = UUID.fromString(get("/auth/me", owner.token).bodyAsMap()!!["id"] as String)
        val leadId = createLeadInTenant(owner.tenantId, "Same-tenant audit probe")

        TransactionTemplate(transactionManager).execute {
            TenantContext.runAs(owner.tenantId) {
                ActorContext.runAs(ownerUserId) {
                    val lead =
                        leadRepository.findActiveById(leadId)
                            ?: error("Lead not found in owner tenant scope")
                    lead.title = "Same-tenant updated"
                    leadRepository.saveAndFlush(lead)
                }
            }
        }

        val rows = auditRepository.findAll().filter { it.resourceId == leadId }
        assertTrue(rows.isEmpty(), "Same-tenant edit should not append to cross_tenant_audit (got ${rows.size} row(s))")
    }

    /**
     * Create a Lead directly through the repository, skipping `LeadService.create`
     * (and the `lead.created` event that triggers an async workflow). The seed
     * pipeline/stage in the freshly-provisioned tenant is picked up by
     * convention; any non-deleted stage in that tenant works.
     */
    private fun createLeadInTenant(
        tenantId: UUID,
        title: String,
    ): UUID =
        TransactionTemplate(transactionManager).execute {
            TenantContext.runAs(tenantId) {
                ActorContext.runAs(tenantId) {
                    val stage =
                        stageRepository.findAll().firstOrNull { it.deletedAt == null && it.tenantId == tenantId }
                            ?: error("No active stage found in tenant $tenantId")
                    val saved =
                        leadRepository.saveAndFlush(
                            Lead().apply {
                                this.title = title
                                this.pipelineId = stage.pipeline.id!!
                                this.stageId = stage.id!!
                            },
                        )
                    saved.id!!
                }
            }
        } ?: error("createLeadInTenant returned null")
}
