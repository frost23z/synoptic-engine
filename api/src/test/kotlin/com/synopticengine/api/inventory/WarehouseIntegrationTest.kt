package com.synopticengine.api.inventory

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarehouseIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list warehouses without token returns 401`() {
        assertEquals(401, get("/api/warehouses", null).status())
    }

    @Test
    fun `list warehouses as VIEWER returns 200`() {
        assertEquals(200, get("/api/warehouses", viewerToken).status())
    }

    @Test
    fun `create warehouse as VIEWER returns 403`() {
        assertEquals(403, post("/api/warehouses", viewerToken, validCreateRequest()).status())
    }

    @Test
    fun `delete warehouse as VIEWER returns 403`() {
        val id = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(403, delete("/api/warehouses/$id", viewerToken).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create warehouse returns 201 with correct fields`() {
        val request = validCreateRequest()
        val result = post("/api/warehouses", adminToken, request)
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(request["name"], body["name"])
    }

    @Test
    fun `create warehouse with blank name returns 422`() {
        assertEquals(422, post("/api/warehouses", adminToken, mapOf("name" to " ")).status())
    }

    @Test
    fun `get warehouse by id returns detail`() {
        val id = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/warehouses/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get warehouse by unknown id returns 404`() {
        assertEquals(404, get("/api/warehouses/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update warehouse returns 200`() {
        val id = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/warehouses/$id",
                adminToken,
                mapOf(
                    "name" to "Updated WH",
                    "contactEmail" to "wh@test.com",
                ),
            )
        assertEquals(200, result.status())
        assertEquals("Updated WH", result.bodyAsMap()!!["name"])
        assertEquals("wh@test.com", result.bodyAsMap()!!["contactEmail"])
    }

    @Test
    fun `delete warehouse returns 204 and is unfindable`() {
        val id = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/warehouses/$id", adminToken).status())
        assertEquals(404, get("/api/warehouses/$id", adminToken).status())
    }

    @Test
    fun `search warehouses returns matching results`() {
        val unique = "WH${UUID.randomUUID().toString().take(6)}"
        post("/api/warehouses", adminToken, mapOf("name" to "Warehouse $unique"))
        val result = get("/api/warehouses/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        assertTrue((result.bodyAsMap()!!["content"] as List<*>).isNotEmpty())
    }

    // ── Locations ─────────────────────────────────────────────────────────

    @Test
    fun `add location returns 201`() {
        val warehouseId = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = post("/api/warehouses/$warehouseId/locations", adminToken, mapOf("name" to "Shelf A1"))
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("Shelf A1", body["name"])
        assertEquals(warehouseId, body["warehouseId"])
    }

    @Test
    fun `list locations returns warehouse locations`() {
        val warehouseId = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        post("/api/warehouses/$warehouseId/locations", adminToken, mapOf("name" to "Shelf B1"))
        post("/api/warehouses/$warehouseId/locations", adminToken, mapOf("name" to "Shelf B2"))

        val result = get("/api/warehouses/$warehouseId/locations", adminToken)
        assertEquals(200, result.status())
        assertEquals(2, result.bodyAsList()!!.size)
    }

    @Test
    fun `update location returns 200`() {
        val warehouseId = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val locationId =
            post(
                "/api/warehouses/$warehouseId/locations",
                adminToken,
                mapOf("name" to "Old Name"),
            ).bodyAsMap()!!["id"] as String

        val result = put("/api/warehouses/$warehouseId/locations/$locationId", adminToken, mapOf("name" to "New Name"))
        assertEquals(200, result.status())
        assertEquals("New Name", result.bodyAsMap()!!["name"])
    }

    @Test
    fun `delete location returns 204`() {
        val warehouseId = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val locationId =
            post(
                "/api/warehouses/$warehouseId/locations",
                adminToken,
                mapOf("name" to "To Delete"),
            ).bodyAsMap()!!["id"] as String

        assertEquals(204, delete("/api/warehouses/$warehouseId/locations/$locationId", adminToken).status())
        assertEquals(0, get("/api/warehouses/$warehouseId/locations", adminToken).bodyAsList()!!.size)
    }

    @Test
    fun `get warehouse products returns stock entries`() {
        val warehouseId = post("/api/warehouses", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val productId =
            post(
                "/api/products",
                adminToken,
                mapOf(
                    "name" to "P-${UUID.randomUUID().toString().take(6)}",
                    "price" to 10.0,
                ),
            ).bodyAsMap()!!["id"] as String
        put("/api/products/$productId/inventory", adminToken, mapOf("warehouseId" to warehouseId, "quantity" to 30))

        val result = get("/api/warehouses/$warehouseId/products", adminToken)
        assertEquals(200, result.status())
        val entries = result.bodyAsList()!!
        assertEquals(1, entries.size)
        assertEquals(30, (entries.first()["quantity"] as Number).toInt())
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validCreateRequest() =
        mapOf(
            "name" to "Warehouse ${UUID.randomUUID().toString().take(8)}",
            "description" to "Test warehouse",
            "contactName" to "John Doe",
        )
}
