package com.synopticengine.api.identity

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoleIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list roles without token returns 401`() {
        assertEquals(401, get("/api/roles", null).status())
    }

    @Test
    fun `list roles as SALESPERSON returns 403`() {
        assertEquals(403, get("/api/roles", salespersonToken).status())
    }

    @Test
    fun `create role without token returns 401`() {
        assertEquals(401, post("/api/roles", null, mapOf("name" to "X", "permissions" to emptyList<String>())).status())
    }

    // ── List ──────────────────────────────────────────────────────────────

    @Test
    fun `list roles as ADMIN returns the four seeded roles`() {
        // V007 seeds 4 roles: ADMIN, MANAGER, SALESPERSON, VIEWER.
        val names = get("/api/roles", adminToken).bodyAsList()!!.map { it["name"] as String }
        assertTrue(names.containsAll(listOf("ADMIN", "MANAGER", "SALESPERSON", "VIEWER")))
    }

    @Test
    fun `list permissions as ADMIN returns all seeded permissions`() {
        // V007 + V008 ship ≥ 20 permission keys.
        assertTrue(get("/api/roles/permissions", adminToken).bodyAsList()!!.size >= 20)
    }

    // ── Get by ID ─────────────────────────────────────────────────────────

    @Test
    fun `get role by id returns role with permissions`() {
        val id = get("/api/roles", adminToken).bodyAsList()!!.first()["id"] as String
        val result = get("/api/roles/$id", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(id, body["id"])
        assertNotNull(body["name"])
        assertNotNull(body["permissions"])
    }

    @Test
    fun `get role by unknown id returns 404`() {
        assertEquals(404, get("/api/roles/${UUID.randomUUID()}", adminToken).status())
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    fun `create role returns 201 with assigned permissions`() {
        val name = "CUSTOM_${UUID.randomUUID().toString().take(8)}"
        val result =
            post(
                "/api/roles",
                adminToken,
                mapOf("name" to name, "description" to "test", "permissions" to listOf("leads.view", "contacts.view")),
            )
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(name, body["name"])
        @Suppress("UNCHECKED_CAST")
        val perms = body["permissions"] as List<String>
        assertTrue(perms.containsAll(listOf("leads.view", "contacts.view")))
    }

    @Test
    fun `create role with duplicate name returns 409`() {
        val name = "DUP_${UUID.randomUUID().toString().take(8)}"
        createRole(name)
        assertEquals(
            409,
            post("/api/roles", adminToken, mapOf("name" to name, "permissions" to emptyList<String>())).status(),
        )
    }

    @Test
    fun `create role with blank name returns 422`() {
        assertEquals(
            422,
            post("/api/roles", adminToken, mapOf("name" to "  ", "permissions" to emptyList<String>())).status(),
        )
    }

    // ── Update ────────────────────────────────────────────────────────────

    @Test
    fun `update role changes name and replaces permissions`() {
        val id = createRole(name = "UPDT_${UUID.randomUUID().toString().take(8)}", permissions = listOf("leads.view"))

        val newName = "NEW_${UUID.randomUUID().toString().take(8)}"
        val result =
            put("/api/roles/$id", adminToken, mapOf("name" to newName, "permissions" to listOf("contacts.view")))
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(newName, body["name"])
        @Suppress("UNCHECKED_CAST")
        val perms = body["permissions"] as List<String>
        assertTrue(perms.contains("contacts.view"))
        assertTrue(!perms.contains("leads.view"))
    }

    @Test
    fun `update role with unknown id returns 404`() {
        assertEquals(
            404,
            put(
                "/api/roles/${UUID.randomUUID()}",
                adminToken,
                mapOf("name" to "X", "permissions" to emptyList<String>()),
            ).status(),
        )
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @Test
    fun `delete role returns 204 and is no longer findable`() {
        val id = createRole()
        assertEquals(204, delete("/api/roles/$id", adminToken).status())
        assertEquals(404, get("/api/roles/$id", adminToken).status())
    }

    @Test
    fun `delete role with unknown id returns 404`() {
        assertEquals(404, delete("/api/roles/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `delete role without token returns 401`() {
        assertEquals(401, delete("/api/roles/${UUID.randomUUID()}", null).status())
    }

    private fun createRole(
        name: String = "R_${UUID.randomUUID().toString().take(8)}",
        permissions: List<String> = emptyList(),
    ): String =
        post("/api/roles", adminToken, mapOf("name" to name, "permissions" to permissions))
            .bodyAsMap()!!["id"] as String
}
