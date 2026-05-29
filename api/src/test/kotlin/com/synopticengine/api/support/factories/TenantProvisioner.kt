package com.synopticengine.api.support.factories

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.identity.service.UserService
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.support.TestAuth
import java.util.UUID

/**
 * Provisions a fresh tenant + admin user and returns the tenant id, a bearer token,
 * and the admin user's id. Used by the cross-tenant sharing tests which need two
 * distinct tenants per scenario.
 */
class TenantProvisioner(
    private val tenantApi: TenantApi,
    private val testAuth: TestAuth,
    private val userService: UserService,
) {
    data class TenantAndToken(
        val tenantId: UUID,
        val token: String,
        val userId: UUID,
    )

    fun provision(prefix: String): TenantAndToken {
        val slug = "$prefix-${UUID.randomUUID().toString().take(6)}"
        val adminEmail = "$prefix-${UUID.randomUUID()}@test.com"
        val password = "Password123!"
        val summary = tenantApi.provision(prefix.replaceFirstChar { it.titlecase() }, slug, adminEmail, password)
        val token = testAuth.login(adminEmail, password)
        val userId =
            TenantContext.runAs(summary.id) {
                userService
                    .findAllActive()
                    .firstOrNull()
                    ?.id
                    ?: error("No user found in newly provisioned tenant ${summary.id}")
            }
        return TenantAndToken(summary.id, token, userId)
    }
}
