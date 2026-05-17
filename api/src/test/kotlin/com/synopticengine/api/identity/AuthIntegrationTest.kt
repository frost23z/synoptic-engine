package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.shared.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
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
        TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
            userService.create(
                email = email,
                password = password,
                firstName = "Auth",
                lastName = "User",
                roleNames = setOf("ADMIN"),
            )
        }
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
        val result = post("/auth/login", null, mapOf("email" to email, "password" to "wrongpassword"))
        assertEquals(400, result.status())
    }

    @Test
    fun `login with unknown email returns 400`() {
        val result = post("/auth/login", null, mapOf("email" to "nobody@nowhere.com", "password" to password))
        assertEquals(400, result.status())
    }

    @Test
    fun `login with missing email returns 400`() {
        val result = post("/auth/login", null, mapOf("password" to password))
        assertEquals(400, result.status())
    }

    @Test
    fun `login with invalid email format returns 422`() {
        val result = post("/auth/login", null, mapOf("email" to "not-an-email", "password" to password))
        assertEquals(422, result.status())
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    @Test
    fun `refresh with valid refresh token returns new access token`() {
        val loginBody = post("/auth/login", null, mapOf("email" to email, "password" to password)).bodyAsMap()!!
        val refreshToken = loginBody["refreshToken"] as String

        val result = post("/auth/refresh", null, mapOf("refreshToken" to refreshToken))

        assertEquals(200, result.status())
        assertNotNull(result.bodyAsMap()!!["accessToken"])
    }

    @Test
    fun `refresh with access token returns 400`() {
        val accessToken = login(email, password)
        val result = post("/auth/refresh", null, mapOf("refreshToken" to accessToken))
        assertEquals(400, result.status())
    }

    @Test
    fun `refresh with garbage token returns 400`() {
        val result = post("/auth/refresh", null, mapOf("refreshToken" to "not.a.valid.token"))
        assertEquals(400, result.status())
    }

    @Test
    fun `refresh with missing field returns 400`() {
        val result = post("/auth/refresh", null, mapOf<String, String>())
        assertEquals(400, result.status())
    }

    // ── GET /me ───────────────────────────────────────────────────────────

    @Test
    fun `GET me with valid token returns current user`() {
        val token = login(email, password)
        val result = get("/auth/me", token)

        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(email, body["email"])
        assertNotNull(body["id"])
        @Suppress("UNCHECKED_CAST")
        val authorities = body["authorities"] as List<*>
        assertTrue(authorities.isNotEmpty())
    }

    @Test
    fun `GET me without token returns 401`() {
        val result = get("/auth/me", null)
        assertEquals(401, result.status())
    }

    // ── Public endpoint availability ──────────────────────────────────────

    @Test
    fun `login endpoint is accessible without auth`() {
        // Should not return 401 — it may return 422 or 400 for bad input, never 401
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andReturn()
        assertTrue(result.status() != 401)
    }
}
