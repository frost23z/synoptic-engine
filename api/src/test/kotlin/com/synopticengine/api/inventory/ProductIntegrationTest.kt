package com.synopticengine.api.inventory

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list products without token returns 401`() {
        assertEquals(401, get("/api/products", null).status())
    }

    @Test
    fun `list products as VIEWER returns 200`() {
        assertEquals(200, get("/api/products", viewerToken).status())
    }

    @Test
    fun `create product as VIEWER returns 403`() {
        assertEquals(403, post("/api/products", viewerToken, validCreateRequest()).status())
    }

    @Test
    fun `delete product as SALESPERSON returns 403`() {
        val id = post("/api/products", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val salesperson = salespersonToken()
        assertEquals(403, delete("/api/products/$id", salesperson).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create product returns 201 with correct fields`() {
        val request = validCreateRequest()
        val result = post("/api/products", adminToken, request)
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals(request["name"], body["name"])
        assertEquals(true, body["isActive"])
    }

    @Test
    fun `create product with duplicate sku returns 409`() {
        val sku = "SKU-${UUID.randomUUID().toString().take(8)}"
        post("/api/products", adminToken, mapOf("name" to "Product A", "sku" to sku, "price" to 10.0))
        val result = post("/api/products", adminToken, mapOf("name" to "Product B", "sku" to sku, "price" to 20.0))
        assertEquals(409, result.status())
    }

    @Test
    fun `create product with blank name returns 422`() {
        assertEquals(422, post("/api/products", adminToken, mapOf("name" to " ", "price" to 10.0)).status())
    }

    @Test
    fun `get product by id returns detail`() {
        val id = post("/api/products", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/products/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get product by unknown id returns 404`() {
        assertEquals(404, get("/api/products/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update product returns 200`() {
        val id = post("/api/products", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/products/$id",
                adminToken,
                mapOf(
                    "name" to "Updated Product",
                    "price" to 999.99,
                    "isActive" to false,
                ),
            )
        assertEquals(200, result.status())
        assertEquals("Updated Product", result.bodyAsMap()!!["name"])
        assertEquals(false, result.bodyAsMap()!!["isActive"])
    }

    @Test
    fun `delete product returns 204 and is unfindable`() {
        val id = post("/api/products", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/products/$id", adminToken).status())
        assertEquals(404, get("/api/products/$id", adminToken).status())
    }

    @Test
    fun `search products returns matching results`() {
        val unique = "SRCH${UUID.randomUUID().toString().take(6)}"
        post("/api/products", adminToken, mapOf("name" to "Product $unique", "price" to 50.0))
        val result = get("/api/products/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        val content = result.bodyAsMap()!!["content"] as List<*>
        assertTrue(content.isNotEmpty())
    }

    // ── Inventory ─────────────────────────────────────────────────────────

    @Test
    fun `set and get product inventory`() {
        val productId = post("/api/products", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val warehouseId = createWarehouse()

        val setResult =
            put(
                "/api/products/$productId/inventory",
                adminToken,
                mapOf(
                    "warehouseId" to warehouseId,
                    "quantity" to 50,
                ),
            )
        assertEquals(200, setResult.status())
        assertEquals(50, (setResult.bodyAsMap()!!["quantity"] as Number).toInt())

        val getResult = get("/api/products/$productId/inventory", adminToken)
        assertEquals(200, getResult.status())
        val entries = getResult.bodyAsList()!!
        assertEquals(1, entries.size)
        assertEquals(50, (entries.first()["quantity"] as Number).toInt())
    }

    @Test
    fun `update product inventory quantity`() {
        val productId = post("/api/products", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val warehouseId = createWarehouse()

        put("/api/products/$productId/inventory", adminToken, mapOf("warehouseId" to warehouseId, "quantity" to 10))
        val updated =
            put(
                "/api/products/$productId/inventory",
                adminToken,
                mapOf(
                    "warehouseId" to warehouseId,
                    "quantity" to 25,
                ),
            )
        assertEquals(200, updated.status())
        assertEquals(25, (updated.bodyAsMap()!!["quantity"] as Number).toInt())
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun createWarehouse(): String =
        post(
            "/api/warehouses",
            adminToken,
            mapOf("name" to "WH-${UUID.randomUUID().toString().take(8)}"),
        ).bodyAsMap()!!["id"] as String

    private fun validCreateRequest() =
        mapOf(
            "name" to "Product ${UUID.randomUUID().toString().take(8)}",
            "price" to 99.99,
            "sku" to "SKU-${UUID.randomUUID().toString().take(8)}",
        )
}
