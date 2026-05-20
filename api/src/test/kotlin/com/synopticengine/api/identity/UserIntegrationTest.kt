package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list users without token returns 401`() {
        assertEquals(401, get("/api/users", null).status())
    }

    @Test
    fun `list users as SALESPERSON returns 403`() {
        assertEquals(403, get("/api/users", salespersonToken).status())
    }

    @Test
    fun `create user without token returns 401`() {
        assertEquals(401, post("/api/users", null, validCreateRequest()).status())
    }

    @Test
    fun `create user as SALESPERSON returns 403`() {
        assertEquals(403, post("/api/users", salespersonToken, validCreateRequest()).status())
    }

    // ── List & search ─────────────────────────────────────────────────────

    @Test
    fun `list users as ADMIN returns non-empty list`() {
        assertTrue(get("/api/users", adminToken).bodyAsList()!!.isNotEmpty())
    }

    @Test
    fun `search users returns matching results`() {
        val unique = UUID.randomUUID().toString().replace("-", "")
        auth.provision(
            roleNames = setOf("VIEWER"),
            email = "search-$unique@test.com",
            firstName = "Unique$unique",
            lastName = "Person",
        )
        assertTrue(get("/api/users/search?q=Unique$unique", adminToken).bodyAsList()!!.isNotEmpty())
    }

    @Test
    fun `search users with no match returns empty list`() {
        assertTrue(get("/api/users/search?q=xyznosuchemail99999abc", adminToken).bodyAsList()!!.isEmpty())
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    fun `create user as ADMIN returns 201 with detail`() {
        val request = validCreateRequest()
        val result = post("/api/users", adminToken, request)
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(request["email"], body["email"])
        assertEquals(request["firstName"], body["firstName"])
        assertEquals(request["lastName"], body["lastName"])
        assertNotNull(body["viewPermission"])
        assertNotNull(body["roles"])
    }

    @Test
    fun `create user with duplicate email returns 409`() {
        val request = validCreateRequest()
        post("/api/users", adminToken, request)
        assertEquals(409, post("/api/users", adminToken, request).status())
    }

    @Test
    fun `create user with invalid email returns 422`() {
        val request = validCreateRequest() + ("email" to "not-an-email")
        assertEquals(422, post("/api/users", adminToken, request).status())
    }

    @Test
    fun `create user with short password returns 422`() {
        val request = validCreateRequest() + ("password" to "short")
        assertEquals(422, post("/api/users", adminToken, request).status())
    }

    @Test
    fun `create user with unknown role returns 400`() {
        val request = validCreateRequest() + ("roles" to listOf("NONEXISTENT_ROLE"))
        assertEquals(400, post("/api/users", adminToken, request).status())
    }

    // ── Get by ID ─────────────────────────────────────────────────────────

    @Test
    fun `get user by id returns full detail with roles and groups`() {
        val id = createUserViaApi()
        val result = get("/api/users/$id", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(id, body["id"])
        assertNotNull(body["viewPermission"])
        assertNotNull(body["roles"])
        assertNotNull(body["groups"])
    }

    @Test
    fun `get user by unknown id returns 404`() {
        assertEquals(404, get("/api/users/${UUID.randomUUID()}", adminToken).status())
    }

    // ── Update ────────────────────────────────────────────────────────────

    @Test
    fun `update user returns 200 with updated fields`() {
        val id = createUserViaApi()
        val result =
            put(
                "/api/users/$id",
                adminToken,
                mapOf(
                    "firstName" to "Updated",
                    "lastName" to "Name",
                    "phone" to "+1234567890",
                ),
            )
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals("Updated", body["firstName"])
        assertEquals("Name", body["lastName"])
        assertEquals("+1234567890", body["phone"])
    }

    @Test
    fun `update user roles replaces existing roles`() {
        val id = createUserViaApi()
        val result =
            put(
                "/api/users/$id",
                adminToken,
                mapOf(
                    "firstName" to "T",
                    "lastName" to "U",
                    "roles" to listOf("VIEWER"),
                ),
            )
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val roles = result.bodyAsMap()!!["roles"] as List<String>
        assertTrue(roles.contains("VIEWER"))
        assertTrue(!roles.contains("SALESPERSON"))
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @Test
    fun `delete user returns 204 and makes user unfindable`() {
        val id = createUserViaApi()
        assertEquals(204, delete("/api/users/$id", adminToken).status())
        assertEquals(404, get("/api/users/$id", adminToken).status())
    }

    @Test
    fun `delete user as SALESPERSON returns 403`() {
        val id = createUserViaApi()
        assertEquals(403, delete("/api/users/$id", salespersonToken).status())
    }

    @Test
    fun `delete user without token returns 401`() {
        assertEquals(401, delete("/api/users/${UUID.randomUUID()}", null).status())
    }

    @Test
    fun `mass destroy deactivates multiple users`() {
        val id1 = createUserViaApi()
        val id2 = createUserViaApi()
        assertEquals(204, post("/api/users/mass-destroy", adminToken, mapOf("ids" to listOf(id1, id2))).status())
        assertEquals(404, get("/api/users/$id1", adminToken).status())
        assertEquals(404, get("/api/users/$id2", adminToken).status())
    }

    @Test
    fun `mass destroy without token returns 401`() {
        assertEquals(401, post("/api/users/mass-destroy", null, mapOf("ids" to emptyList<String>())).status())
    }

    private fun createUserViaApi(): String =
        post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String

    private fun validCreateRequest() =
        mapOf(
            "email" to "user-${UUID.randomUUID()}@test.com",
            "password" to "password123",
            "firstName" to "Test",
            "lastName" to "User",
            "roles" to listOf("SALESPERSON"),
        )
}
