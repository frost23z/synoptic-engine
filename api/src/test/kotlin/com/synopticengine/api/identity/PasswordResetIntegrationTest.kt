package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class PasswordResetIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `POST auth forgot-password returns 204 for any email (silent)`() {
        val result =
            post(
                "/auth/forgot-password",
                null,
                mapOf("email" to "nobody@example.com"),
            )
        assertEquals(204, result.status(), result.response.contentAsString)
    }

    @Test
    fun `POST auth reset-password returns 400 for invalid token`() {
        val result =
            post(
                "/auth/reset-password",
                null,
                mapOf("token" to "bad-token", "email" to "admin@synoptic.dev", "newPassword" to "newPass123"),
            )
        assertEquals(400, result.status(), result.response.contentAsString)
    }

    @Test
    fun `POST auth forgot-password is rate limited`() {
        val email = "forgot-${UUID.randomUUID()}@example.com"
        repeat(5) {
            assertEquals(204, post("/auth/forgot-password", null, mapOf("email" to email)).status())
        }
        assertEquals(429, post("/auth/forgot-password", null, mapOf("email" to email)).status())
    }
}
