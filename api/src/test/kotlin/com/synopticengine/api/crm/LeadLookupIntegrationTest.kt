package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the lead-source and lead-type lookup controllers (`/api/lead-sources`,
 * `/api/lead-types`) — small CRUD endpoints that back the lead create/edit forms.
 * Both controllers share an identical shape, so the cases mirror each other.
 */
class LeadLookupIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── lead-sources ──────────────────────────────────────────────────────

    @Test
    fun `list lead-sources without token returns 401`() {
        assertEquals(401, get("/api/lead-sources", null).status())
    }

    @Test
    fun `list lead-sources as VIEWER returns 200`() {
        assertEquals(200, get("/api/lead-sources", viewerToken).status())
    }

    @Test
    fun `create lead-source as VIEWER returns 403`() {
        assertEquals(403, post("/api/lead-sources", viewerToken, mapOf("name" to "Web")).status())
    }

    @Test
    fun `create lead-source returns 201 then appears in the list`() {
        val name = "Source-${UUID.randomUUID().toString().take(8)}"
        val result = post("/api/lead-sources", adminToken, mapOf("name" to name))
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(name, body["name"])

        val names = get("/api/lead-sources", adminToken).bodyAsList()!!.map { it["name"] }
        assertTrue(names.contains(name))
    }

    @Test
    fun `create duplicate lead-source returns 409`() {
        val name = "Dup-${UUID.randomUUID().toString().take(8)}"
        assertEquals(201, post("/api/lead-sources", adminToken, mapOf("name" to name)).status())
        assertEquals(409, post("/api/lead-sources", adminToken, mapOf("name" to name)).status())
    }

    @Test
    fun `create lead-source with blank name returns 422`() {
        assertEquals(422, post("/api/lead-sources", adminToken, mapOf("name" to "")).status())
    }

    @Test
    fun `update lead-source returns 200 with new name`() {
        val id = post("/api/lead-sources", adminToken, mapOf("name" to "Before")).bodyAsMap()!!["id"]
        val newName = "After-${UUID.randomUUID().toString().take(6)}"
        val result = put("/api/lead-sources/$id", adminToken, mapOf("name" to newName))
        assertEquals(200, result.status())
        assertEquals(newName, result.bodyAsMap()!!["name"])
    }

    @Test
    fun `update unknown lead-source returns 404`() {
        assertEquals(404, put("/api/lead-sources/${UUID.randomUUID()}", adminToken, mapOf("name" to "X")).status())
    }

    @Test
    fun `delete lead-source returns 204 then 404 on re-fetch via update`() {
        val id = post("/api/lead-sources", adminToken, mapOf("name" to "ToDelete")).bodyAsMap()!!["id"]
        assertEquals(204, delete("/api/lead-sources/$id", adminToken).status())
        assertEquals(404, put("/api/lead-sources/$id", adminToken, mapOf("name" to "Y")).status())
    }

    @Test
    fun `delete unknown lead-source returns 404`() {
        assertEquals(404, delete("/api/lead-sources/${UUID.randomUUID()}", adminToken).status())
    }

    // ── lead-types ────────────────────────────────────────────────────────

    @Test
    fun `list lead-types without token returns 401`() {
        assertEquals(401, get("/api/lead-types", null).status())
    }

    @Test
    fun `create lead-type returns 201 then appears in the list`() {
        val name = "Type-${UUID.randomUUID().toString().take(8)}"
        val result = post("/api/lead-types", adminToken, mapOf("name" to name))
        assertEquals(201, result.status())
        assertEquals(name, result.bodyAsMap()!!["name"])

        val names = get("/api/lead-types", adminToken).bodyAsList()!!.map { it["name"] }
        assertTrue(names.contains(name))
    }

    @Test
    fun `create duplicate lead-type returns 409`() {
        val name = "DupType-${UUID.randomUUID().toString().take(8)}"
        assertEquals(201, post("/api/lead-types", adminToken, mapOf("name" to name)).status())
        assertEquals(409, post("/api/lead-types", adminToken, mapOf("name" to name)).status())
    }

    @Test
    fun `create lead-type as VIEWER returns 403`() {
        assertEquals(403, post("/api/lead-types", viewerToken, mapOf("name" to "Inbound")).status())
    }

    @Test
    fun `update then delete lead-type round-trips`() {
        val id = post("/api/lead-types", adminToken, mapOf("name" to "T1")).bodyAsMap()!!["id"]
        assertEquals(200, put("/api/lead-types/$id", adminToken, mapOf("name" to "T2")).status())
        assertEquals(204, delete("/api/lead-types/$id", adminToken).status())
        assertEquals(404, delete("/api/lead-types/$id", adminToken).status())
    }
}
