package com.synopticengine.api.inventory

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ProductFactory
import com.synopticengine.api.support.factories.WarehouseFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the inventory movement controller under `/api/inventory`: stock state,
 * reserve/release, the append-only movement ledger, and the low-stock reorder query.
 * Seeds on-hand via `PUT /api/products/{id}/inventory` which sets the absolute on-hand
 * quantity for a (product, warehouse, location) tuple.
 */
class InventoryMovementIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var warehouseFactory: WarehouseFactory

    @Autowired private lateinit var productFactory: ProductFactory

    private lateinit var adminToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    private fun createLocation(warehouseId: UUID): UUID {
        val result =
            post(
                "/api/warehouses/$warehouseId/locations",
                adminToken,
                mapOf("name" to "Loc-${UUID.randomUUID().toString().take(6)}"),
            )
        assertEquals(201, result.status(), result.response.contentAsString)
        return UUID.fromString(result.bodyAsMap()!!["id"] as String)
    }

    private fun setStock(
        productId: UUID,
        warehouseId: UUID,
        locationId: UUID,
        qty: Int,
    ) {
        val result =
            put(
                "/api/products/$productId/inventory",
                adminToken,
                mapOf("warehouseId" to warehouseId, "warehouseLocationId" to locationId, "quantity" to qty),
            )
        assertEquals(200, result.status(), result.response.contentAsString)
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `get stock without token returns 401`() {
        assertEquals(401, get("/api/inventory/stock?productId=${UUID.randomUUID()}", null).status())
    }

    @Test
    fun `reserve without token returns 401`() {
        assertEquals(401, post("/api/inventory/reserve", null, emptyMap<String, Any>()).status())
    }

    // ── Stock state ───────────────────────────────────────────────────────

    @Test
    fun `get stock without warehouseId returns 400`() {
        val productId = productFactory.id(adminToken)
        assertEquals(400, get("/api/inventory/stock?productId=$productId", adminToken).status())
    }

    @Test
    fun `set inventory then get stock reflects on-hand and available`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val locationId = createLocation(warehouseId)
        val productId = productFactory.id(adminToken)
        setStock(productId, warehouseId, locationId, 100)

        val result =
            get(
                "/api/inventory/stock?productId=$productId&warehouseId=$warehouseId&locationId=$locationId",
                adminToken,
            )
        assertEquals(200, result.status())
        val entry = result.bodyAsList()!!.single()
        assertEquals(100, entry["onHand"])
        assertEquals(0, entry["reserved"])
        assertEquals(100, entry["available"])
    }

    // ── Reserve / release ─────────────────────────────────────────────────

    @Test
    fun `reserve decrements available and is reflected in stock`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val locationId = createLocation(warehouseId)
        val productId = productFactory.id(adminToken)
        setStock(productId, warehouseId, locationId, 100)

        val reserve =
            post(
                "/api/inventory/reserve",
                adminToken,
                mapOf("productId" to productId, "locationId" to locationId, "qty" to 30),
            )
        assertEquals(204, reserve.status(), reserve.response.contentAsString)

        val entry =
            get(
                "/api/inventory/stock?productId=$productId&warehouseId=$warehouseId&locationId=$locationId",
                adminToken,
            ).bodyAsList()!!.single()
        assertEquals(30, entry["reserved"])
        assertEquals(70, entry["available"])
    }

    @Test
    fun `reserve more than available returns 409`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val locationId = createLocation(warehouseId)
        val productId = productFactory.id(adminToken)
        setStock(productId, warehouseId, locationId, 5)

        val reserve =
            post(
                "/api/inventory/reserve",
                adminToken,
                mapOf("productId" to productId, "locationId" to locationId, "qty" to 50),
            )
        assertEquals(409, reserve.status())
    }

    @Test
    fun `release returns reserved stock to available`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val locationId = createLocation(warehouseId)
        val productId = productFactory.id(adminToken)
        setStock(productId, warehouseId, locationId, 100)

        post(
            "/api/inventory/reserve",
            adminToken,
            mapOf(
                "productId" to productId,
                "locationId" to locationId,
                "qty" to 40,
            ),
        )
        val release =
            post(
                "/api/inventory/release",
                adminToken,
                mapOf("productId" to productId, "locationId" to locationId, "qty" to 40),
            )
        assertEquals(204, release.status(), release.response.contentAsString)

        val entry =
            get(
                "/api/inventory/stock?productId=$productId&warehouseId=$warehouseId&locationId=$locationId",
                adminToken,
            ).bodyAsList()!!.single()
        assertEquals(0, entry["reserved"])
        assertEquals(100, entry["available"])
    }

    @Test
    fun `reserve with qty below 1 returns 422`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val locationId = createLocation(warehouseId)
        val productId = productFactory.id(adminToken)
        val result =
            post(
                "/api/inventory/reserve",
                adminToken,
                mapOf("productId" to productId, "locationId" to locationId, "qty" to 0),
            )
        assertEquals(422, result.status())
    }

    // ── Movement ledger ───────────────────────────────────────────────────

    @Test
    fun `movements ledger records reserve and release entries`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val locationId = createLocation(warehouseId)
        val productId = productFactory.id(adminToken)
        setStock(productId, warehouseId, locationId, 100)

        post(
            "/api/inventory/reserve",
            adminToken,
            mapOf(
                "productId" to productId,
                "locationId" to locationId,
                "qty" to 10,
            ),
        )
        post(
            "/api/inventory/release",
            adminToken,
            mapOf(
                "productId" to productId,
                "locationId" to locationId,
                "qty" to 10,
            ),
        )

        val movements = get("/api/inventory/movements?productId=$productId", adminToken).bodyAsList()!!
        val types = movements.map { it["movementType"] }
        assertTrue(types.contains("RESERVE"), "expected a RESERVE movement, got $types")
        assertTrue(types.contains("RELEASE"), "expected a RELEASE movement, got $types")
    }

    // ── Low stock / reorder ───────────────────────────────────────────────

    @Test
    fun `low-stock lists a product whose on-hand is at or below its reorder threshold`() {
        val name = "LowStock-${UUID.randomUUID().toString().take(8)}"
        val productId =
            UUID.fromString(
                post(
                    "/api/products",
                    adminToken,
                    mapOf("name" to name, "price" to "10.00", "reorderThreshold" to 10),
                ).bodyAsMap()!!["id"] as String,
            )

        val lowStock = get("/api/inventory/low-stock", adminToken).bodyAsList()!!
        assertTrue(
            lowStock.any { it["productId"] == productId.toString() },
            "expected $name (0 on-hand, threshold 10) in low-stock list",
        )
    }
}
