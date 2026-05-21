package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthIntegrationTest : AbstractIntegrationTest() {
    private lateinit var email: String
    private val password = "password123"

    @BeforeEach
    fun setup() {
        email = "auth-test-${UUID.randomUUID()}@test.com"
        auth.provision(roleNames = setOf("ADMIN"), email = email, firstName = "Auth", lastName = "User")
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @Test
    fun `login with valid credentials returns tokens`() {
        val result = post("/auth/login", null, mapOf("email" to email, "password" to password))
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["accessToken"])
        assertNotNull(body["refreshToken"])
        assertEquals("Bearer", body["tokenType"])
        assertEquals(email, body["email"])
    }

    @Test
    fun `login with wrong password returns 400`() {
        assertEquals(400, post("/auth/login", null, mapOf("email" to email, "password" to "wrong")).status())
    }

    @Test
    fun `login with unknown email returns 400`() {
        assertEquals(
            400,
            post("/auth/login", null, mapOf("email" to "nobody@nowhere.com", "password" to password)).status(),
        )
    }

    @Test
    fun `login with missing email returns 400`() {
        assertEquals(400, post("/auth/login", null, mapOf("password" to password)).status())
    }

    @Test
    fun `login with invalid email format returns 422`() {
        assertEquals(422, post("/auth/login", null, mapOf("email" to "not-an-email", "password" to password)).status())
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    @Test
    fun `refresh with valid refresh token returns new access token`() {
        val refreshToken =
            post("/auth/login", null, mapOf("email" to email, "password" to password))
                .bodyAsMap()!!["refreshToken"] as String
        val result = post("/auth/refresh", null, mapOf("refreshToken" to refreshToken))
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsMap()!!["accessToken"])
    }

    @Test
    fun `refresh token rotation revokes reused token family`() {
        val firstRefresh =
            post("/auth/login", null, mapOf("email" to email, "password" to password))
                .bodyAsMap()!!["refreshToken"] as String
        val secondRefresh =
            post("/auth/refresh", null, mapOf("refreshToken" to firstRefresh))
                .bodyAsMap()!!["refreshToken"] as String
        assertEquals(400, post("/auth/refresh", null, mapOf("refreshToken" to firstRefresh)).status())
        assertEquals(400, post("/auth/refresh", null, mapOf("refreshToken" to secondRefresh)).status())
    }

    @Test
    fun `refresh with access token returns 400`() {
        val accessToken = login(email, password)
        assertEquals(400, post("/auth/refresh", null, mapOf("refreshToken" to accessToken)).status())
    }

    @Test
    fun `refresh with garbage token returns 400`() {
        assertEquals(400, post("/auth/refresh", null, mapOf("refreshToken" to "not.a.valid.token")).status())
    }

    @Test
    fun `refresh with missing field returns 400`() {
        assertEquals(400, post("/auth/refresh", null, mapOf<String, String>()).status())
    }

    // ── GET /me ───────────────────────────────────────────────────────────

    @Test
    fun `GET me with valid token returns current user and authorities`() {
        val token = login(email, password)
        val result = get("/auth/me", token)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(email, body["email"])
        assertNotNull(body["id"])
        @Suppress("UNCHECKED_CAST")
        assertTrue((body["authorities"] as List<*>).isNotEmpty())
    }

    @Test
    fun `GET me without token returns 401`() {
        assertEquals(401, get("/auth/me", null).status())
    }

    @Test
    fun `login endpoint does not reject empty body with 401 (would mask validation)`() {
        // Should return 400/422 for bad input, never 401 — the endpoint must be auth-free.
        assertTrue(post("/auth/login", null, mapOf<String, String>()).status() != 401)
    }
}
