package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GroupIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list groups without token returns 401`() {
        assertEquals(401, get("/api/groups", null).status())
    }

    @Test
    fun `list groups as SALESPERSON returns 403`() {
        assertEquals(403, get("/api/groups", salespersonToken).status())
    }

    @Test
    fun `create group as SALESPERSON returns 403`() {
        assertEquals(403, post("/api/groups", salespersonToken, mapOf("name" to "x")).status())
    }

    @Test
    fun `create group without token returns 401`() {
        assertEquals(401, post("/api/groups", null, mapOf("name" to "x")).status())
    }

    @Test
    fun `delete group as SALESPERSON returns 403`() {
        val id = createGroup()
        assertEquals(403, delete("/api/groups/$id", salespersonToken).status())
    }

    // ── List ──────────────────────────────────────────────────────────────

    @Test
    fun `list groups as ADMIN returns list`() {
        val result = get("/api/groups", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    fun `create group returns 201 with correct fields`() {
        val name = "Test Group ${UUID.randomUUID().toString().take(8)}"
        val result = post("/api/groups", adminToken, mapOf("name" to name, "description" to "A test group"))
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(name, body["name"])
        assertEquals("A test group", body["description"])
        assertNotNull(body["createdAt"])
    }

    @Test
    fun `create group with duplicate name returns 409`() {
        val name = "DUP-${UUID.randomUUID().toString().take(8)}"
        createGroup(name)
        assertEquals(409, post("/api/groups", adminToken, mapOf("name" to name)).status())
    }

    @Test
    fun `create group with blank name returns 422`() {
        assertEquals(422, post("/api/groups", adminToken, mapOf("name" to "   ")).status())
    }

    // ── Get by ID ─────────────────────────────────────────────────────────

    @Test
    fun `get group by id returns group detail`() {
        val id = createGroup()
        val result = get("/api/groups/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get group by unknown id returns 404`() {
        assertEquals(404, get("/api/groups/${UUID.randomUUID()}", adminToken).status())
    }

    // ── Update ────────────────────────────────────────────────────────────

    @Test
    fun `update group changes name and description`() {
        val id = createGroup()
        val newName = "Updated Group ${UUID.randomUUID().toString().take(8)}"
        val result = put("/api/groups/$id", adminToken, mapOf("name" to newName, "description" to "Updated desc"))
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(newName, body["name"])
        assertEquals("Updated desc", body["description"])
    }

    @Test
    fun `update group with duplicate name returns 409`() {
        val name1 = "GRP_A_${UUID.randomUUID().toString().take(8)}"
        createGroup(name1)
        val id2 = createGroup()
        assertEquals(409, put("/api/groups/$id2", adminToken, mapOf("name" to name1)).status())
    }

    @Test
    fun `update group with unknown id returns 404`() {
        assertEquals(404, put("/api/groups/${UUID.randomUUID()}", adminToken, mapOf("name" to "X")).status())
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @Test
    fun `delete group returns 204 and is no longer findable`() {
        val id = createGroup()
        assertEquals(204, delete("/api/groups/$id", adminToken).status())
        assertEquals(404, get("/api/groups/$id", adminToken).status())
    }

    @Test
    fun `delete group with unknown id returns 404`() {
        assertEquals(404, delete("/api/groups/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `delete group without token returns 401`() {
        assertEquals(401, delete("/api/groups/${UUID.randomUUID()}", null).status())
    }

    // ── User with group assignment ─────────────────────────────────────────

    @Test
    fun `create user with group attaches the group`() {
        val groupId = createGroup()
        val result =
            post(
                "/api/users",
                adminToken,
                mapOf(
                    "email" to "grouped-${UUID.randomUUID()}@test.com",
                    "password" to "password123",
                    "firstName" to "Grouped",
                    "lastName" to "User",
                    "roles" to listOf("SALESPERSON"),
                    "groups" to listOf(groupId),
                ),
            )
        assertEquals(201, result.status())
        @Suppress("UNCHECKED_CAST")
        val groups = result.bodyAsMap()!!["groups"] as List<Map<String, String>>
        assertEquals(1, groups.size)
        assertEquals(groupId, groups.first()["id"])
    }

    private fun createGroup(name: String = "G-${UUID.randomUUID().toString().take(8)}"): String =
        post("/api/groups", adminToken, mapOf("name" to name, "description" to "test")).bodyAsMap()!!["id"] as String
}
