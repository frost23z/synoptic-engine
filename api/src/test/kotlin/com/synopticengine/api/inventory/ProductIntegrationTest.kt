package com.synopticengine.api.inventory

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ProductFactory
import com.synopticengine.api.support.factories.WarehouseFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var productFactory: ProductFactory

    @Autowired private lateinit var warehouseFactory: WarehouseFactory

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
        assertEquals(403, post("/api/products", viewerToken, mapOf("name" to "x", "price" to 1)).status())
    }

    @Test
    fun `delete product as SALESPERSON returns 403`() {
        val id = productFactory.id(adminToken)
        assertEquals(403, delete("/api/products/$id", salespersonToken()).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create product returns 201 with isActive true`() {
        val body = productFactory.create(adminToken)
        assertNotNull(body["id"])
        assertNotNull(body["name"])
        assertEquals(true, body["isActive"])
    }

    @Test
    fun `create product with duplicate sku returns 409`() {
        val sku = "SKU-${UUID.randomUUID().toString().take(8)}"
        productFactory.create(adminToken, sku = sku)
        assertEquals(
            409,
            post(
                "/api/products",
                adminToken,
                mapOf("name" to "Product B", "sku" to sku, "price" to BigDecimal("20.0")),
            ).status(),
        )
    }

    @Test
    fun `create product with blank name returns 422`() {
        assertEquals(422, post("/api/products", adminToken, mapOf("name" to " ", "price" to 10.0)).status())
    }

    @Test
    fun `get product by id returns detail`() {
        val id = productFactory.id(adminToken)
        val result = get("/api/products/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id.toString(), result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get product by unknown id returns 404`() {
        assertEquals(404, get("/api/products/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update product returns 200 with updated fields`() {
        val id = productFactory.id(adminToken)
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
        val id = productFactory.id(adminToken)
        assertEquals(204, delete("/api/products/$id", adminToken).status())
        assertEquals(404, get("/api/products/$id", adminToken).status())
    }

    @Test
    fun `search products returns matching results`() {
        val unique = "SRCH${UUID.randomUUID().toString().take(6)}"
        productFactory.create(adminToken, name = "Product $unique")
        val result = get("/api/products/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        assertTrue((result.bodyAsMap()!!["content"] as List<*>).isNotEmpty())
    }

    // ── Inventory ─────────────────────────────────────────────────────────

    @Test
    fun `set inventory then update reflects new quantity`() {
        val productId = productFactory.id(adminToken)
        val warehouseId = warehouseFactory.id(adminToken)

        val initial =
            put(
                "/api/products/$productId/inventory",
                adminToken,
                mapOf(
                    "warehouseId" to warehouseId.toString(),
                    "quantity" to 50,
                ),
            )
        assertEquals(200, initial.status())
        assertEquals(50, (initial.bodyAsMap()!!["onHand"] as Number).toInt())

        val updated =
            put(
                "/api/products/$productId/inventory",
                adminToken,
                mapOf(
                    "warehouseId" to warehouseId.toString(),
                    "quantity" to 25,
                ),
            )
        assertEquals(200, updated.status())
        assertEquals(25, (updated.bodyAsMap()!!["onHand"] as Number).toInt())

        val getResult = get("/api/products/$productId/inventory", adminToken)
        assertEquals(200, getResult.status())
        val entries = getResult.bodyAsList()!!
        assertEquals(1, entries.size)
        assertEquals(25, (entries.first()["onHand"] as Number).toInt())
    }
}
