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

    // ── List ──────────────────────────────────────────────────────────────

    @Test
    fun `list users as ADMIN returns non-empty list`() {
        val result = get("/api/users", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.isNotEmpty())
    }

    // ── Search ────────────────────────────────────────────────────────────

    @Test
    fun `search users returns matching results`() {
        val unique = UUID.randomUUID().toString().replace("-", "")
        userService.create(
            email = "search-$unique@test.com",
            password = "password123",
            firstName = "Unique$unique",
            lastName = "Person",
            roleNames = setOf("VIEWER"),
        )

        val result = get("/api/users/search?q=Unique$unique", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.isNotEmpty())
    }

    @Test
    fun `search users with no match returns empty list`() {
        val result = get("/api/users/search?q=xyznosuchemail99999abc", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertTrue(body.isEmpty())
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
        val result = post("/api/users", adminToken, request)
        assertEquals(409, result.status())
    }

    @Test
    fun `create user with invalid email returns 422`() {
        val request =
            mapOf(
                "email" to "not-an-email",
                "password" to "password123",
                "firstName" to "Test",
                "lastName" to "User",
            )
        assertEquals(422, post("/api/users", adminToken, request).status())
    }

    @Test
    fun `create user with short password returns 422`() {
        val request =
            mapOf(
                "email" to "short-${UUID.randomUUID()}@test.com",
                "password" to "short",
                "firstName" to "Test",
                "lastName" to "User",
            )
        assertEquals(422, post("/api/users", adminToken, request).status())
    }

    @Test
    fun `create user with invalid role returns 400`() {
        val request =
            mapOf(
                "email" to "badrole-${UUID.randomUUID()}@test.com",
                "password" to "password123",
                "firstName" to "Test",
                "lastName" to "User",
                "roles" to listOf("NONEXISTENT_ROLE"),
            )
        assertEquals(400, post("/api/users", adminToken, request).status())
    }

    // ── Get by ID ─────────────────────────────────────────────────────────

    @Test
    fun `get user by id returns full detail`() {
        val created = post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!
        val id = created["id"] as String

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
        val id = post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String

        val update = mapOf("firstName" to "Updated", "lastName" to "Name", "phone" to "+1234567890")
        val result = put("/api/users/$id", adminToken, update)

        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals("Updated", body["firstName"])
        assertEquals("Name", body["lastName"])
        assertEquals("+1234567890", body["phone"])
    }

    @Test
    fun `update user roles replaces existing roles`() {
        val id = post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String

        val update = mapOf("firstName" to "Test", "lastName" to "User", "roles" to listOf("VIEWER"))
        val result = put("/api/users/$id", adminToken, update)

        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val roles = result.bodyAsMap()!!["roles"] as List<String>
        assertTrue(roles.contains("VIEWER"))
        assertTrue(!roles.contains("SALESPERSON"))
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @Test
    fun `delete user returns 204 and makes user unfindable`() {
        val id = post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String

        assertEquals(204, delete("/api/users/$id", adminToken).status())
        assertEquals(404, get("/api/users/$id", adminToken).status())
    }

    @Test
    fun `delete user as SALESPERSON returns 403`() {
        val id = post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(403, delete("/api/users/$id", salespersonToken).status())
    }

    @Test
    fun `delete user without token returns 401`() {
        assertEquals(401, delete("/api/users/${UUID.randomUUID()}", null).status())
    }

    // ── Mass destroy ──────────────────────────────────────────────────────

    @Test
    fun `mass destroy deactivates multiple users`() {
        val id1 = post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val id2 = post("/api/users", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String

        val result = post("/api/users/mass-destroy", adminToken, mapOf("ids" to listOf(id1, id2)))
        assertEquals(204, result.status())

        assertEquals(404, get("/api/users/$id1", adminToken).status())
        assertEquals(404, get("/api/users/$id2", adminToken).status())
    }

    @Test
    fun `mass destroy without token returns 401`() {
        assertEquals(401, post("/api/users/mass-destroy", null, mapOf("ids" to emptyList<String>())).status())
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validCreateRequest() =
        mapOf(
            "email" to "user-${UUID.randomUUID()}@test.com",
            "password" to "password123",
            "firstName" to "Test",
            "lastName" to "User",
            "roles" to listOf("SALESPERSON"),
        )
}
