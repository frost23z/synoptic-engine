package com.synopticengine.api

import com.synopticengine.api.crm.contact.repo.OrganizationRepository
import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.OrganizationFactory
import com.synopticengine.api.support.factories.PersonFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

/**
 * T2.2 — Explicit tenant predicate on dashboard native queries.
 *
 * Verifies that the native-SQL dashboard queries now carry explicit `tenant_id = :tenantId`
 * predicates, so counts are correctly scoped to a single tenant without relying solely on
 * Postgres RLS (which is not enforced in the test DB because Testcontainers runs as a
 * non-BYPASSRLS `synoptic_app` role — but in the test setup this is still the case that
 * RLS fires, so the explicit predicate is tested via direct repository calls with
 * [TenantContext] isolation).
 *
 * Test strategy:
 *  1. Provision two tenants A and B.
 *  2. Create one lead / person / org in Tenant A.
 *  3. Assert that Tenant A's native count queries return exactly 1.
 *  4. Assert that Tenant B's native count queries return 0 (no cross-tenant bleed).
 */
class DashboardTenantIsolationIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var personFactory: PersonFactory

    @Autowired private lateinit var organizationFactory: OrganizationFactory

    @Autowired private lateinit var leadRepository: LeadRepository

    @Autowired private lateinit var personRepository: PersonRepository

    @Autowired private lateinit var organizationRepository: OrganizationRepository

    @Test
    fun `lead countCreatedInRangeNative returns only the creating tenant's leads`() {
        val a = tenantProvisioner.provision("dash-lead-a")
        val b = tenantProvisioner.provision("dash-lead-b")

        val before = Instant.now().minusSeconds(10)
        leadFactory.create(a.token, title = "DashLeadA-${UUID.randomUUID().toString().take(6)}")
        val after = Instant.now().plusSeconds(10)

        // Tenant A should count 1 lead in the window.
        val countA = leadRepository.countCreatedInRangeNative(a.tenantId, before, after, false, listOf(UUID(0L, 0L)))
        assertEquals(
            1,
            countA.toInt(),
            "Tenant A must count exactly 1 lead created in the window",
        )

        // Tenant B must count 0 — it has no leads.
        val countB = leadRepository.countCreatedInRangeNative(b.tenantId, before, after, false, listOf(UUID(0L, 0L)))
        assertEquals(
            0,
            countB.toInt(),
            "Tenant B must count 0 leads — the predicate must prevent cross-tenant bleed",
        )
    }

    @Test
    fun `openLeadsByStageNative returns only the querying tenant's open leads`() {
        val a = tenantProvisioner.provision("dash-stage-a")
        val b = tenantProvisioner.provision("dash-stage-b")

        leadFactory.create(a.token, title = "OpenLeadA-${UUID.randomUUID().toString().take(6)}")

        val rowsA = leadRepository.openLeadsByStageNative(a.tenantId, false, listOf(UUID(0L, 0L)))
        val rowsB = leadRepository.openLeadsByStageNative(b.tenantId, false, listOf(UUID(0L, 0L)))

        // Tenant A must have at least 1 open-lead stage row.
        assertEquals(
            true,
            rowsA.isNotEmpty(),
            "Tenant A must have at least one open stage row",
        )
        // Total leads across all Tenant A stage rows = 1.
        val totalLeadsA = rowsA.sumOf { (it[1] as Number).toInt() }
        assertEquals(
            1,
            totalLeadsA,
            "Tenant A must count exactly 1 open lead across all stages",
        )

        // Tenant B should have no rows or zero leads in its rows.
        val totalLeadsB = rowsB.sumOf { (it[1] as Number).toInt() }
        assertEquals(
            0,
            totalLeadsB,
            "Tenant B must have 0 open leads — only Tenant A's lead was created",
        )
    }

    @Test
    fun `person countCreatedInRangeNative returns only the creating tenant's persons`() {
        val a = tenantProvisioner.provision("dash-person-a")
        val b = tenantProvisioner.provision("dash-person-b")

        val before = Instant.now().minusSeconds(10)
        personFactory.create(a.token)
        val after = Instant.now().plusSeconds(10)

        val countA = personRepository.countCreatedInRangeNative(a.tenantId, before, after)
        val countB = personRepository.countCreatedInRangeNative(b.tenantId, before, after)

        assertEquals(1, countA.toInt(), "Tenant A must count exactly 1 person")
        assertEquals(0, countB.toInt(), "Tenant B must count 0 persons")
    }

    @Test
    fun `organization countCreatedInRangeNative returns only the creating tenant's organizations`() {
        val a = tenantProvisioner.provision("dash-org-a")
        val b = tenantProvisioner.provision("dash-org-b")

        val before = Instant.now().minusSeconds(10)
        organizationFactory.create(a.token)
        val after = Instant.now().plusSeconds(10)

        val countA = organizationRepository.countCreatedInRangeNative(a.tenantId, before, after)
        val countB = organizationRepository.countCreatedInRangeNative(b.tenantId, before, after)

        assertEquals(1, countA.toInt(), "Tenant A must count exactly 1 organization")
        assertEquals(0, countB.toInt(), "Tenant B must count 0 organizations")
    }
}
