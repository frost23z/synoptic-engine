package com.synopticengine.api.sharing

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.repo.LeadRepository
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
 * [com.synopticengine.api.sharing.CrossTenantWriteListener] (JPA `@PostUpdate` on
 * Lead/Person/Organization/Product) → [com.synopticengine.api.sharing.events.SharedRecordEditedEvent]
 * → [com.synopticengine.api.sharing.events.CrossTenantAuditEventListener] →
 * `CrossTenantAuditService.record(..., EDIT)`.
 *
 * The test bypasses the HTTP layer for the cross-tenant update because the
 * service-layer "find own + shared" merge is still on the deferred list (Phase 2.5
 * follow-up). Once that lands, the same assertion holds over a real PUT
 * /api/leads/{id} from the consumer's token.
 */
class CrossTenantAuditIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var tenantApi: TenantApi

    @Autowired
    lateinit var leadRepository: LeadRepository

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

        // Owner creates a lead.
        val ownerToken = login(ownerEmail, pw)
        val pipelines = get("/api/pipelines", ownerToken).bodyAsList()!!
        val defaultPipeline = pipelines.first { it["isDefault"] == true }
        val pipelineId = UUID.fromString(defaultPipeline["id"] as String)
        @Suppress("UNCHECKED_CAST")
        val stageId =
            UUID.fromString(
                (defaultPipeline["stages"] as List<Map<String, Any>>).first()["id"] as String,
            )
        val leadId =
            UUID.fromString(
                post(
                    "/api/leads",
                    ownerToken,
                    mapOf(
                        "title" to "Cross-tenant audit probe",
                        "pipelineId" to pipelineId.toString(),
                        "stageId" to stageId.toString(),
                    ),
                ).bodyAsMap()!!["id"] as String,
            )

        // Cross-tenant edit: simulate the consumer modifying the owner's lead
        // (the path that the service-layer "own + shared" merge will eventually
        // expose via PUT /api/leads/{id} from the consumer's token).
        val tx = TransactionTemplate(transactionManager)
        tx.execute {
            TenantContext.runAs(owner.id) {
                ActorContext.runAs(consumerUserId) {
                    val lead =
                        leadRepository.findActiveById(leadId)
                            ?: error("Lead not found in owner tenant scope")
                    // Switch the runtime tenant to the consumer's: this is the
                    // boundary the CrossTenantWriteListener uses to decide whether
                    // the write is cross-tenant. The owning row is loaded in the
                    // owner's session above; the save below runs as if the consumer
                    // is the actor.
                    TenantContext.runAs(consumer.id) {
                        lead.title = "Edited by consumer"
                        // saveAndFlush forces the UPDATE + @PostUpdate to fire inside
                        // the consumer's TenantContext. Plain save() would defer the
                        // flush to outer-transaction commit, by which point `runAs`
                        // has already restored the previous TenantContext.
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

        val pipelines = get("/api/pipelines", ownerToken).bodyAsList()!!
        val defaultPipeline = pipelines.first { it["isDefault"] == true }
        val pipelineId = UUID.fromString(defaultPipeline["id"] as String)
        @Suppress("UNCHECKED_CAST")
        val stageId =
            UUID.fromString(
                (defaultPipeline["stages"] as List<Map<String, Any>>).first()["id"] as String,
            )
        val leadId =
            UUID.fromString(
                post(
                    "/api/leads",
                    ownerToken,
                    mapOf(
                        "title" to "Same-tenant edit",
                        "pipelineId" to pipelineId.toString(),
                        "stageId" to stageId.toString(),
                    ),
                ).bodyAsMap()!!["id"] as String,
            )

        // Same-tenant edit via the repository (bypassing async workflow races on PUT).
        val tx = TransactionTemplate(transactionManager)
        tx.execute {
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
}
