package com.synopticengine.api

import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T7.1 — True RLS enforcement test.
 *
 * Proves that the PostgreSQL RLS policies (not the Hibernate `@Filter`) block
 * cross-tenant row reads when the connection runs as the `synoptic_app`
 * NOBYPASSRLS role.
 *
 * How the NOBYPASSRLS role is engaged:
 *   `application-test.yaml` sets `spring.datasource.hikari.connection-init-sql:
 *   SET ROLE synoptic_app`. Every Hikari connection in the test pool is therefore
 *   bound to the `synoptic_app` role (NOBYPASSRLS = true), which means the
 *   Postgres RLS policies in V007 **do** fire for every query — including the
 *   native SQL queries in this test that carry **no** `tenant_id` predicate.
 *
 * Isolation mechanism under test:
 *   [RlsTenantGucAspect] runs `SET LOCAL app.current_tenant = '<uuid>'` at the
 *   start of every `@Transactional` method when [TenantContext] is populated.
 *   The RLS policies check `app_current_tenant()`, so the GUC is what drives
 *   per-row filtering. The [RlsNativeSqlReader] used below intentionally uses
 *   raw native SQL with no `tenant_id` column predicate — if RLS were not
 *   working the query would return all rows from both tenants.
 */
@Import(RlsNativeSqlReader::class)
class RlsTwoTenantIsolationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var rlsNativeReader: RlsNativeSqlReader

    @Test
    fun `tenant A cannot read tenant B leads via native SQL without tenant_id predicate`() {
        val a = tenantProvisioner.provision("rls2a")
        val b = tenantProvisioner.provision("rls2b")

        // Create one lead per tenant through the full HTTP stack so the
        // @PrePersist hook stamps the correct tenant_id.
        val leadAId = UUID.fromString(leadFactory.create(a.token)["id"] as String)
        val leadBId = UUID.fromString(leadFactory.create(b.token)["id"] as String)

        // Native SQL with NO tenant_id predicate — row filtering is purely via RLS.
        val idsSeenByA = TenantContext.runAs(a.tenantId) { rlsNativeReader.allNonDeletedLeadIds() }
        val idsSeenByB = TenantContext.runAs(b.tenantId) { rlsNativeReader.allNonDeletedLeadIds() }

        assertTrue(leadAId in idsSeenByA, "Tenant A must see its own lead")
        assertTrue(leadBId !in idsSeenByA, "Tenant A must NOT see tenant B's lead (RLS block)")

        assertTrue(leadBId in idsSeenByB, "Tenant B must see its own lead")
        assertTrue(leadAId !in idsSeenByB, "Tenant B must NOT see tenant A's lead (RLS block)")
    }

    @Test
    fun `null TenantContext bypasses RLS (IS NULL branch) and sees all rows`() {
        val a = tenantProvisioner.provision("rls2null-a")
        val b = tenantProvisioner.provision("rls2null-b")

        val leadAId = UUID.fromString(leadFactory.create(a.token)["id"] as String)
        val leadBId = UUID.fromString(leadFactory.create(b.token)["id"] as String)

        // Without a TenantContext the GUC is empty, and the RLS policy's
        // `app_current_tenant() IS NULL` clause bypasses filtering — this is
        // the intentional behaviour for bootstrap and public endpoints.
        val idsSeenByNull = rlsNativeReader.allNonDeletedLeadIds()

        assertTrue(leadAId in idsSeenByNull, "Null-tenant context should see tenant A's lead (bypass)")
        assertTrue(leadBId in idsSeenByNull, "Null-tenant context should see tenant B's lead (bypass)")
    }

    @Test
    fun `two-tenant write-then-read, each tenant sees exactly its own row count`() {
        val a = tenantProvisioner.provision("rls2count-a")
        val b = tenantProvisioner.provision("rls2count-b")

        val aLeadsBefore = TenantContext.runAs(a.tenantId) { rlsNativeReader.allNonDeletedLeadIds() }.size
        val bLeadsBefore = TenantContext.runAs(b.tenantId) { rlsNativeReader.allNonDeletedLeadIds() }.size

        // Write 2 leads in A, 3 in B.
        repeat(2) { leadFactory.create(a.token) }
        repeat(3) { leadFactory.create(b.token) }

        val aLeadsAfter = TenantContext.runAs(a.tenantId) { rlsNativeReader.allNonDeletedLeadIds() }.size
        val bLeadsAfter = TenantContext.runAs(b.tenantId) { rlsNativeReader.allNonDeletedLeadIds() }.size

        assertEquals(aLeadsBefore + 2, aLeadsAfter, "Tenant A should see exactly 2 new rows")
        assertEquals(bLeadsBefore + 3, bLeadsAfter, "Tenant B should see exactly 3 new rows")
    }
}

/**
 * Issues raw native SQL against the `leads` table with **no** tenant_id column
 * predicate. Row visibility is determined entirely by the Postgres RLS policy
 * (via `SET LOCAL app.current_tenant` issued by [RlsTenantGucAspect]).
 *
 * `REQUIRES_NEW` guarantees a fresh transaction so the GUC is set by the aspect
 * before any query executes, regardless of any outer transaction context.
 */
@Component
open class RlsNativeSqlReader(
    private val entityManager: EntityManager,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun allNonDeletedLeadIds(): List<UUID> {
        // No tenant_id predicate — RLS is the sole isolation mechanism here.
        @Suppress("UNCHECKED_CAST")
        val raw =
            entityManager
                .createNativeQuery("SELECT id FROM leads WHERE deleted_at IS NULL")
                .resultList as List<Any>
        return raw.map { it as UUID }
    }
}
