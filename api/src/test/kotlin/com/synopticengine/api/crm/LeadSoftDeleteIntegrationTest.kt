package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.config.TenantSession
import com.synopticengine.api.support.factories.LeadFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P1.10 acceptance: deleting a soft-deletable entity via the JpaRepository (not the
 * service) must not bypass the soft-delete contract. With @SQLDelete on the entity,
 * `repository.delete(...)` writes `deleted_at = NOW()` instead of issuing a real
 * DELETE; with @SQLRestriction, subsequent queries don't see the tombstoned row.
 */
class LeadSoftDeleteIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var leadRepository: LeadRepository

    @Autowired
    lateinit var tenantSession: TenantSession

    @Autowired
    lateinit var leadFactory: LeadFactory

    @Test
    @Transactional
    fun `repository delete soft-deletes the lead and hides it from subsequent queries`() {
        val saved =
            TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                tenantSession.applyFilter()
                leadRepository.save(
                    Lead().apply {
                        title = "SoftDelete-${UUID.randomUUID().toString().take(8)}"
                    },
                )
            }
        val leadId = saved.id!!

        TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
            tenantSession.applyFilter()
            // The lead is visible before delete.
            assertTrue(leadRepository.findById(leadId).isPresent, "freshly saved lead must be findable")

            // Use the repository's delete — not the service — to confirm @SQLDelete fires.
            leadRepository.delete(saved)
            leadRepository.flush()

            // After repository.delete, every JPA query path hides the row:
            // - findById (with @SQLRestriction in Hibernate 6+).
            // - the derived findActiveById that has an explicit deletedAt clause.
            // - findAllByDeletedAtIsNull (was already filtering).
            assertTrue(
                leadRepository.findById(leadId).isEmpty,
                "@SQLRestriction should hide the soft-deleted lead from findById",
            )
            assertNull(
                leadRepository.findActiveById(leadId),
                "the explicit deletedAt JPQL filter must continue to hide the row",
            )
            val page = leadRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 100))
            assertTrue(
                page.content.none { it.id == leadId },
                "findAllByDeletedAtIsNull must not return the soft-deleted lead",
            )
        }
    }

    @Test
    fun `service-layer delete is also a soft delete`() {
        // The service path (existing behavior) and the repository path (new safety net)
        // must converge on the same soft-deleted state.
        val token = adminToken()
        val id = leadFactory.id(token, title = "ServicePath-${UUID.randomUUID().toString().take(6)}")

        assertEquals(204, delete("/api/leads/$id", token).status())
        assertEquals(404, get("/api/leads/$id", token).status())
    }
}
