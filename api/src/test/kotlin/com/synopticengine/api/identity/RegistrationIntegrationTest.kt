package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Self-serve signup (`POST /auth/register`): a brand-new company can register itself, gets its
 * first admin auto-logged-in, and that admin is fully scoped to the freshly-provisioned tenant.
 */
class RegistrationIntegrationTest : AbstractIntegrationTest() {
    private fun uniqueEmail() = "signup-${UUID.randomUUID()}@example.com"

    @Test
    fun `register creates a company and returns admin tokens`() {
        val email = uniqueEmail()
        val result =
            post(
                "/auth/register",
                null,
                mapOf("companyName" to "Acme Robotics", "email" to email, "password" to "password123"),
            )
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["accessToken"])
        assertNotNull(body["refreshToken"])
        assertNotNull(body["tenantId"])
        assertEquals(email, body["email"])
        @Suppress("UNCHECKED_CAST")
        assertTrue((body["authorities"] as List<*>).isNotEmpty(), "first user should be a full admin")
    }

    @Test
    fun `registered admin lands in its own tenant and can hit tenant-scoped endpoints`() {
        val token =
            post(
                "/auth/register",
                null,
                mapOf("companyName" to "Globex", "email" to uniqueEmail(), "password" to "password123"),
            ).bodyAsMap()!!["accessToken"] as String
        // New tenant: an empty leads list (200), not a 500/403 — proves the tenant + RLS context resolve.
        assertEquals(200, get("/api/leads", token).status())
    }

    @Test
    fun `registering with an already-used email returns 409`() {
        val email = uniqueEmail()
        val first =
            post(
                "/auth/register",
                null,
                mapOf("companyName" to "First Co", "email" to email, "password" to "password123"),
            )
        assertEquals(201, first.status())
        val second =
            post(
                "/auth/register",
                null,
                mapOf("companyName" to "Second Co", "email" to email, "password" to "password123"),
            )
        assertEquals(409, second.status())
    }

    @Test
    fun `register with a too-short password returns 422`() {
        assertEquals(
            422,
            post(
                "/auth/register",
                null,
                mapOf("companyName" to "Tiny Pass", "email" to uniqueEmail(), "password" to "short"),
            ).status(),
        )
    }

    @Test
    fun `register with a blank company name returns 422`() {
        assertEquals(
            422,
            post(
                "/auth/register",
                null,
                mapOf("companyName" to " ", "email" to uniqueEmail(), "password" to "password123"),
            ).status(),
        )
    }

    @Test
    fun `register endpoint is public (does not require auth)`() {
        assertTrue(
            post("/auth/register", null, mapOf<String, String>()).status() != 401,
            "register must be permit-listed; bad input should be 4xx validation, never 401",
        )
    }
}
