package com.synopticengine.api

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.shared.TenantContext
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Phase 4 P0-1: the application must run `SET LOCAL app.current_tenant = '<uuid>'`
 * inside every transaction so the RLS policies in `V040__rls_per_tenant.sql`
 * actually resolve. Pre-fix, the GUC was never set and every policy fell
 * through the `app_current_tenant() IS NULL OR …` bypass clause.
 *
 * The test reads back the GUC inside a `@Transactional` method (which the
 * `RlsTenantGucAspect` intercepts) and asserts it matches the [TenantContext].
 * We can't assert "RLS filtered out a row from another tenant" here because
 * Testcontainers' default Postgres user is superuser (BYPASSRLS = true) —
 * the policies don't take effect for tests. The GUC's presence is the only
 * thing this PR can verify in CI; deployment to a non-superuser app role
 * makes the policies live.
 */
@Import(GucReader::class)
class RlsTenantGucIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var gucReader: GucReader

    @Autowired
    lateinit var tenantApi: TenantApi

    @Test
    fun `SET LOCAL app current_tenant is run inside every transactional method when TenantContext is set`() {
        val tenant =
            tenantApi.provision(
                "RlsGuc",
                "rls-guc-${UUID.randomUUID().toString().take(6)}",
                "rls-guc-${UUID.randomUUID()}@test.com",
                "Password123!",
            )

        val readBack: String? =
            TenantContext.runAs(tenant.id) {
                gucReader.readGuc()
            }
        assertEquals(tenant.id.toString(), readBack, "Expected the GUC to be set to the active tenant's id")
    }

    @Test
    fun `SET LOCAL is skipped when TenantContext is unset (bootstrap, public endpoints)`() {
        // Outside a TenantContext, the GUC remains empty, and the RLS policies'
        // `IS NULL` bypass clause takes effect — this is the intended behaviour
        // for bootstrap and public endpoints.
        val readBack: String? = gucReader.readGuc()
        assertNull(readBack?.takeIf { it.isNotBlank() }, "Expected the GUC to be empty outside a TenantContext")
    }
}

@Component
open class GucReader(
    private val entityManager: EntityManager,
) {
    /**
     * Reads `app.current_tenant` from inside a fresh transaction so the
     * `RlsTenantGucAspect` fires its SET LOCAL before this query runs.
     * `REQUIRES_NEW` makes sure the transaction is independent of any
     * surrounding test harness transaction (`AbstractIntegrationTest` doesn't
     * open one today, but the propagation keeps the test robust).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun readGuc(): String? =
        entityManager
            .createNativeQuery("SELECT current_setting('app.current_tenant', true)")
            .singleResult as String?
}
