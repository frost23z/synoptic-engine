package com.synopticengine.api.support

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
    fun adminToken(): String = tokenFor(setOf("ADMIN"))

    fun salespersonToken(): String = tokenFor(setOf("SALESPERSON"))

    fun tokenFor(
        roleNames: Set<String>,
        tenantId: UUID = TenantContext.SEED_TENANT_ID,
    ): String {
        val email = "test-${UUID.randomUUID()}@test.com"
        TenantContext.runAs(tenantId) {
            userService.create(
                email = email,
                password = "password123",
                firstName = "Test",
                lastName = "User",
                roleNames = roleNames,
            )
        }
        return login(email, "password123")
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
