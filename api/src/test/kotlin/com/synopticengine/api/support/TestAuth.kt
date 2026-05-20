package com.synopticengine.api.support

import com.synopticengine.api.identity.domain.ViewPermission
import com.synopticengine.api.identity.service.UserService
import com.synopticengine.api.shared.TenantContext
import java.util.UUID

/**
 * Identity-aware test helper: provisions users and issues tokens through the real
 * login flow. Kept separate from [TestHttp] so tests that don't touch identity can
 * skip the [UserService] dependency.
 */
class TestAuth(
    private val userService: UserService,
    private val http: TestHttp,
) {
    /** Email + bearer token for a freshly-provisioned user. */
    data class Credentials(
        val email: String,
        val token: String,
    )

    fun adminToken(): String = tokenFor(setOf("ADMIN"))

    fun salespersonToken(): String = tokenFor(setOf("SALESPERSON"))

    /** SALESPERSON whose ViewPermission is INDIVIDUAL — i.e. can only see records they own. */
    fun individualSalespersonToken(): String =
        provision(setOf("SALESPERSON"), viewPermission = ViewPermission.INDIVIDUAL).token

    fun tokenFor(
        roleNames: Set<String>,
        tenantId: UUID = TenantContext.SEED_TENANT_ID,
        viewPermission: ViewPermission = ViewPermission.GLOBAL,
    ): String = provision(roleNames, tenantId, viewPermission).token

    fun provision(
        roleNames: Set<String>,
        tenantId: UUID = TenantContext.SEED_TENANT_ID,
        viewPermission: ViewPermission = ViewPermission.GLOBAL,
        email: String = "test-${UUID.randomUUID()}@test.com",
        firstName: String = "Test",
        lastName: String = "User",
    ): Credentials {
        TenantContext.runAs(tenantId) {
            userService.create(
                email = email,
                password = "password123",
                firstName = firstName,
                lastName = lastName,
                roleNames = roleNames,
                viewPermission = viewPermission,
            )
        }
        return Credentials(email = email, token = login(email, "password123"))
    }

    fun login(
        email: String,
        password: String,
    ): String {
        val result =
            http.post(
                "/auth/login",
                token = null,
                body = mapOf("email" to email, "password" to password),
            )
        val body =
            http.bodyAsMap(result)
                ?: error("login returned empty body (status=${result.response.status})")
        return body["accessToken"] as String
    }
}
