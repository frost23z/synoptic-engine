package com.synopticengine.api.inventory

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ProductFactory
import com.synopticengine.api.support.factories.WarehouseFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarehouseIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var warehouseFactory: WarehouseFactory

    @Autowired private lateinit var productFactory: ProductFactory

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
        assertEquals(403, post("/api/warehouses", viewerToken, mapOf("name" to "x")).status())
    }

    @Test
    fun `delete warehouse as VIEWER returns 403`() {
        val id = warehouseFactory.id(adminToken)
        assertEquals(403, delete("/api/warehouses/$id", viewerToken).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create warehouse returns 201 with id and name`() {
        val body = warehouseFactory.create(adminToken)
        assertNotNull(body["id"])
        assertNotNull(body["name"])
    }

    @Test
    fun `create warehouse with blank name returns 422`() {
        assertEquals(422, post("/api/warehouses", adminToken, mapOf("name" to " ")).status())
    }

    @Test
    fun `get warehouse by id returns detail`() {
        val id = warehouseFactory.id(adminToken)
        val result = get("/api/warehouses/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id.toString(), result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get warehouse by unknown id returns 404`() {
        assertEquals(404, get("/api/warehouses/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update warehouse returns 200 with updated fields`() {
        val id = warehouseFactory.id(adminToken)
        val result =
            put("/api/warehouses/$id", adminToken, mapOf("name" to "Updated WH", "contactEmail" to "wh@test.com"))
        assertEquals(200, result.status())
        assertEquals("Updated WH", result.bodyAsMap()!!["name"])
        assertEquals("wh@test.com", result.bodyAsMap()!!["contactEmail"])
    }

    @Test
    fun `delete warehouse returns 204 and is unfindable`() {
        val id = warehouseFactory.id(adminToken)
        assertEquals(204, delete("/api/warehouses/$id", adminToken).status())
        assertEquals(404, get("/api/warehouses/$id", adminToken).status())
    }

    @Test
    fun `search warehouses returns matching results`() {
        val unique = "WH${UUID.randomUUID().toString().take(6)}"
        warehouseFactory.create(adminToken, name = "Warehouse $unique")
        val result = get("/api/warehouses/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        assertTrue((result.bodyAsMap()!!["content"] as List<*>).isNotEmpty())
    }

    // ── Locations ─────────────────────────────────────────────────────────

    @Test
    fun `add update list and delete locations`() {
        val warehouseId = warehouseFactory.id(adminToken)

        val a1 = createLocation(warehouseId, "Shelf A1")
        assertEquals("Shelf A1", a1["name"])
        assertEquals(warehouseId.toString(), a1["warehouseId"])

        val a2id = createLocation(warehouseId, "Shelf A2")["id"] as String

        // List shows both.
        assertEquals(2, get("/api/warehouses/$warehouseId/locations", adminToken).bodyAsList()!!.size)

        // Update name.
        val updated = put("/api/warehouses/$warehouseId/locations/$a2id", adminToken, mapOf("name" to "Shelf A2-new"))
        assertEquals(200, updated.status())
        assertEquals("Shelf A2-new", updated.bodyAsMap()!!["name"])

        // Delete leaves only the survivor.
        assertEquals(204, delete("/api/warehouses/$warehouseId/locations/$a2id", adminToken).status())
        assertEquals(1, get("/api/warehouses/$warehouseId/locations", adminToken).bodyAsList()!!.size)
    }

    @Test
    fun `get warehouse products returns stock entries`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val productId = productFactory.id(adminToken)
        put(
            "/api/products/$productId/inventory",
            adminToken,
            mapOf("warehouseId" to warehouseId.toString(), "quantity" to 30),
        )

        val result = get("/api/warehouses/$warehouseId/products", adminToken)
        assertEquals(200, result.status())
        val entries = result.bodyAsList()!!
        assertEquals(1, entries.size)
        assertEquals(30, (entries.first()["quantity"] as Number).toInt())
    }

    private fun createLocation(
        warehouseId: UUID,
        name: String,
    ): Map<String, Any> =
        post("/api/warehouses/$warehouseId/locations", adminToken, mapOf("name" to name)).bodyAsMap()!!
}
