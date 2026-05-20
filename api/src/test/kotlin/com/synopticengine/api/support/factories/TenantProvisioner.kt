package com.synopticengine.api.support.factories

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.support.TestAuth
import java.util.UUID

/**
 * Provisions a fresh tenant + admin user and returns the tenant id plus a
 * bearer token for that user. Used by the cross-tenant sharing tests, which
 * need two distinct tenants per scenario.
 */
class TenantProvisioner(
    private val tenantApi: TenantApi,
    private val testAuth: TestAuth,
) {
    data class TenantAndToken(
        val tenantId: UUID,
        val token: String,
    )

    fun provision(prefix: String): TenantAndToken {
        val slug = "$prefix-${UUID.randomUUID().toString().take(6)}"
        val adminEmail = "$prefix-${UUID.randomUUID()}@test.com"
        val password = "Password123!"
        val summary = tenantApi.provision(prefix.replaceFirstChar { it.titlecase() }, slug, adminEmail, password)
        return TenantAndToken(summary.id, testAuth.login(adminEmail, password))
    }
}
