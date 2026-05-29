package com.synopticengine.api

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * T2.3 — Cross-tenant FK trigger regression test.
 *
 * Verifies that the triggers installed by [V018__cross_tenant_fk_triggers.sql]
 * reject INSERT/UPDATE operations that would create a FK edge between rows in
 * different tenants. For example, a `quotes` row whose `lead_id` points at a lead
 * owned by a different tenant must be rejected.
 *
 * The test uses a direct JDBC INSERT (via [EntityManager.createNativeQuery]) to
 * bypass the application's JPQL layer and hit the trigger directly — the same way a
 * bug elsewhere in the application might produce a cross-tenant edge.
 *
 * **Guardrail**: sharing-module tables do NOT have these triggers (cross-tenant by
 * design); this test only covers tenant-scoped tables.
 */
@Import(CrossTenantFkInserter::class)
class CrossTenantFkTriggerIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var crossTenantFkInserter: CrossTenantFkInserter

    @Test
    fun `inserting a quote that references a lead from a different tenant is rejected by the trigger`() {
        val a = tenantProvisioner.provision("fk-trigger-a")
        val b = tenantProvisioner.provision("fk-trigger-b")

        // Create a lead in Tenant A.
        val leadAId =
            UUID.fromString(
                leadFactory.create(
                    a.token,
                    title = "TriggerTestLead-${UUID.randomUUID().toString().take(6)}",
                )["id"] as String,
            )

        // Attempt to INSERT a quote in Tenant B that references Tenant A's lead.
        // The trigger check_quote_tenant_matches_lead() should reject this.
        assertThrows(PersistenceException::class.java) {
            TenantContext.runAs(b.tenantId) {
                crossTenantFkInserter.insertQuoteWithForeignLead(
                    quoteId = UUID.randomUUID(),
                    quoteTenantId = b.tenantId,
                    leadId = leadAId, // cross-tenant: leadA belongs to Tenant A!
                )
            }
        }
    }

    @Test
    fun `inserting a quote that references a lead from the same tenant succeeds`() {
        val a = tenantProvisioner.provision("fk-trigger-same-a")

        val leadId =
            UUID.fromString(
                leadFactory.create(
                    a.token,
                    title = "SameTenantLead-${UUID.randomUUID().toString().take(6)}",
                )["id"] as String,
            )

        // Same-tenant reference must succeed (no exception).
        TenantContext.runAs(a.tenantId) {
            // Clean up: the crossTenantFkInserter.insertQuoteWithForeignLead succeeds for same-tenant
            // We verify by checking that no exception is thrown
            // (a full success assertion would require querying quotes, which adds test complexity)
            try {
                crossTenantFkInserter.insertQuoteWithForeignLead(
                    quoteId = UUID.randomUUID(),
                    quoteTenantId = a.tenantId,
                    leadId = leadId,
                )
            } catch (e: Exception) {
                throw AssertionError("Same-tenant FK insert should succeed but got: ${e.message}", e)
            }
        }
    }
}

/**
 * Helper bean that performs raw native SQL inserts to bypass application-layer
 * validation and directly exercise the DB-level triggers.
 *
 * Each method runs in `REQUIRES_NEW` so the outer test transaction is not affected
 * (the trigger violation rolls back only the inner transaction, not the test's).
 */
@Component
class CrossTenantFkInserter(
    private val entityManager: EntityManager,
) {
    /**
     * Inserts a minimal `quotes` row referencing [leadId] as the lead FK.
     * The trigger `trg_quote_tenant_matches_lead` will fire and reject the row
     * if `leadId` belongs to a different tenant than [quoteTenantId].
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertQuoteWithForeignLead(
        quoteId: UUID,
        quoteTenantId: UUID,
        leadId: UUID,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO quotes (
                    id, version, tenant_id, lead_id,
                    title, status, discount, tax, adjustment,
                    created_at, updated_at
                ) VALUES (
                    :id, 0, :tenantId, :leadId,
                    'Trigger Test Quote', 'draft', 0, 0, 0,
                    NOW(), NOW()
                )
                """,
            ).setParameter("id", quoteId)
            .setParameter("tenantId", quoteTenantId)
            .setParameter("leadId", leadId)
            .executeUpdate()
    }
}
