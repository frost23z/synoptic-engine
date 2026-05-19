package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.shared.ActorContext
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.sharing.domain.CrossTenantAction
import com.synopticengine.api.sharing.repo.CrossTenantAuditRepository
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
    @Autowired
    lateinit var tenantApi: TenantApi

    @Autowired
    lateinit var leadRepository: LeadRepository

    @Autowired
    lateinit var stageRepository: StageRepository

    @Autowired
    lateinit var auditRepository: CrossTenantAuditRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `cross-tenant edit of a shared lead writes an EDIT row to cross_tenant_audit`() {
        val ownerSlug = "audit-owner-${UUID.randomUUID().toString().take(6)}"
        val consumerSlug = "audit-consumer-${UUID.randomUUID().toString().take(6)}"
        val ownerEmail = "audit-owner-${UUID.randomUUID()}@test.com"
        val consumerEmail = "audit-consumer-${UUID.randomUUID()}@test.com"
        val pw = "Password123!"
        val owner = tenantApi.provision("AuditOwner", ownerSlug, ownerEmail, pw)
        val consumer = tenantApi.provision("AuditConsumer", consumerSlug, consumerEmail, pw)

        // Find the consumer-side admin user id so the audit row's actorUserId is real.
        val consumerToken = login(consumerEmail, pw)
        val consumerUserId =
            UUID.fromString(get("/auth/me", consumerToken).bodyAsMap()!!["id"] as String)

        val leadId = createLeadInTenant(owner.id, "Cross-tenant audit probe")

        // Cross-tenant edit: simulate the consumer modifying the owner's lead.
        TransactionTemplate(transactionManager).execute {
            TenantContext.runAs(owner.id) {
                ActorContext.runAs(consumerUserId) {
                    val lead =
                        leadRepository.findActiveById(leadId)
                            ?: error("Lead not found in owner tenant scope")
                    // Switch the runtime tenant to the consumer's. The interceptor
                    // uses this to detect cross-tenant writes.
                    TenantContext.runAs(consumer.id) {
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
        assertEquals(owner.id, row.ownerTenantId)
        assertEquals(consumer.id, row.actorTenantId)
        assertEquals(consumerUserId, row.actorUserId)
        assertEquals("leads", row.resourceType)
        assertEquals(leadId, row.resourceId)
        assertNotNull(row.at)
    }

    @Test
    fun `same-tenant edit on a shareable entity does not write to cross_tenant_audit`() {
        val ownerSlug = "audit-same-${UUID.randomUUID().toString().take(6)}"
        val ownerEmail = "audit-same-${UUID.randomUUID()}@test.com"
        val pw = "Password123!"
        val owner = tenantApi.provision("AuditSame", ownerSlug, ownerEmail, pw)
        val ownerToken = login(ownerEmail, pw)
        val ownerUserId =
            UUID.fromString(get("/auth/me", ownerToken).bodyAsMap()!!["id"] as String)

        val leadId = createLeadInTenant(owner.id, "Same-tenant audit probe")

        TransactionTemplate(transactionManager).execute {
            TenantContext.runAs(owner.id) {
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
        assertTrue(
            rows.isEmpty(),
            "Same-tenant edit should not append to cross_tenant_audit (got ${rows.size} row(s))",
        )
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
                // The seed-tenant admin is the user-of-record for these test rows;
                // ActorContext is set so `AuditableEntity.createdBy` is populated.
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
