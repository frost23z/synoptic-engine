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

    // ── PUT /me ───────────────────────────────────────────────────────────

    @Test
    fun `PUT me updates name and phone and returns updated profile`() {
        val token = login(email, password)
        val result =
            put(
                "/auth/me",
                token,
                mapOf(
                    "firstName" to "Updated",
                    "lastName" to "Name",
                    "phone" to "+1-555-0100",
                ),
            )
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals("Updated Name", body["fullName"])
        assertEquals(email, body["email"])
    }

    @Test
    fun `PUT me without token returns 401`() {
        assertEquals(
            401,
            put("/auth/me", null, mapOf("firstName" to "X", "lastName" to "Y")).status(),
        )
    }

    @Test
    fun `PUT me with blank firstName returns 422`() {
        val token = login(email, password)
        assertEquals(422, put("/auth/me", token, mapOf("firstName" to " ", "lastName" to "Y")).status())
    }

    @Test
    fun `PUT me changes password when currentPassword is correct`() {
        val token = login(email, password)
        val result =
            put(
                "/auth/me",
                token,
                mapOf(
                    "firstName" to "Auth",
                    "lastName" to "User",
                    "currentPassword" to password,
                    "newPassword" to "newpass456",
                ),
            )
        assertEquals(200, result.status())
        // Verify new password works
        assertNotNull(login(email, "newpass456"))
    }

    @Test
    fun `PUT me rejects password change when currentPassword is wrong`() {
        val token = login(email, password)
        val result =
            put(
                "/auth/me",
                token,
                mapOf(
                    "firstName" to "Auth",
                    "lastName" to "User",
                    "currentPassword" to "wrong-password",
                    "newPassword" to "newpass456",
                ),
            )
        assertEquals(400, result.status())
    }

    // ── Sessions ──────────────────────────────────────────────────────────

    @Test
    fun `GET sessions without token returns 401`() {
        assertEquals(401, get("/auth/sessions", null).status())
    }

    @Test
    fun `GET sessions returns active sessions list`() {
        val token = login(email, password)
        val result = get("/auth/sessions", token)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.isNotEmpty(), "at least the current session should be listed")
        assertNotNull(body[0]["id"])
        assertNotNull(body[0]["issuedAt"])
        assertNotNull(body[0]["expiresAt"])
    }

    @Test
    fun `DELETE sessions revokes a session`() {
        val token = login(email, password)
        val sessions = get("/auth/sessions", token).bodyAsList()!!
        val sessionId = sessions[0]["id"] as String
        assertEquals(204, delete("/auth/sessions/$sessionId", token).status())
    }

    // ── Login history ─────────────────────────────────────────────────────

    @Test
    fun `GET login-history without token returns 401`() {
        assertEquals(401, get("/auth/login-history", null).status())
    }

    @Test
    fun `GET login-history returns at least one entry after login`() {
        val token = login(email, password)
        val result = get("/auth/login-history", token)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.isNotEmpty(), "login should have been recorded")
        assertNotNull(body[0]["id"])
        assertNotNull(body[0]["loggedInAt"])
    }

    // ── MFA / TOTP ────────────────────────────────────────────────────────

    @Test
    fun `POST mfa-setup without token returns 401`() {
        assertEquals(401, post("/auth/mfa/setup", null, emptyMap<String, String>()).status())
    }

    @Test
    fun `POST mfa-setup returns secret and qrUri`() {
        val token = login(email, password)
        val result = post("/auth/mfa/setup", token, emptyMap<String, String>())
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["secret"])
        assertNotNull(body["qrUri"])
        assertTrue((body["qrUri"] as String).startsWith("otpauth://totp/"))
    }

    @Test
    fun `POST mfa-confirm with wrong code returns 400`() {
        val token = login(email, password)
        post("/auth/mfa/setup", token, emptyMap<String, String>())
        assertEquals(400, post("/auth/mfa/confirm", token, mapOf("code" to "000000")).status())
    }

    @Test
    fun `DELETE mfa without token returns 401`() {
        assertEquals(401, delete("/auth/mfa", null).status())
    }

    @Test
    fun `POST mfa-verify with invalid token returns 400`() {
        assertEquals(
            400,
            post("/auth/mfa/verify", null, mapOf("mfaToken" to "not-a-token", "code" to "000000")).status(),
        )
    }

    @Test
    fun `login does not require MFA when MFA is not enabled`() {
        val result = post("/auth/login", null, mapOf("email" to email, "password" to password))
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(false, body["mfaRequired"])
        assertNotNull(body["accessToken"])
    }

    // ── API keys ──────────────────────────────────────────────────────────

    @Test
    fun `POST api-keys without token returns 401`() {
        assertEquals(401, post("/auth/api-keys", null, mapOf("name" to "test")).status())
    }

    @Test
    fun `GET api-keys without token returns 401`() {
        assertEquals(401, get("/auth/api-keys", null).status())
    }

    @Test
    fun `POST api-keys creates key and returns full key once`() {
        val token = login(email, password)
        val result = post("/auth/api-keys", token, mapOf("name" to "ci-key"))
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertNotNull(body["key"])
        assertTrue((body["key"] as String).startsWith("sk_"), "key must start with sk_")
        assertNotNull(body["prefix"])
        assertEquals("ci-key", body["name"])
    }

    @Test
    fun `GET api-keys lists created key without exposing raw value`() {
        val token = login(email, password)
        post("/auth/api-keys", token, mapOf("name" to "list-test-key"))
        val result = get("/auth/api-keys", token)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.isNotEmpty())
        val key = body[0]
        assertNotNull(key["id"])
        assertNotNull(key["prefix"])
        assertTrue(key["key"] == null, "raw key must not appear in list response")
    }

    @Test
    fun `DELETE api-keys revokes key`() {
        val token = login(email, password)
        val createBody = post("/auth/api-keys", token, mapOf("name" to "to-revoke")).bodyAsMap()!!
        val keyId = createBody["id"] as String
        assertEquals(204, delete("/auth/api-keys/$keyId", token).status())
    }

    @Test
    fun `API key bearer token authenticates successfully`() {
        val token = login(email, password)
        val rawKey =
            post("/auth/api-keys", token, mapOf("name" to "bearer-test"))
                .bodyAsMap()!!["key"] as String
        val meResult = get("/auth/me", rawKey)
        assertEquals(200, meResult.status())
        assertEquals(email, meResult.bodyAsMap()!!["email"])
    }
}
