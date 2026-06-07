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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the stock-transfer lifecycle (`/api/inventory/transfers`):
 * create → dispatch (PENDING→IN_TRANSIT) → receive (IN_TRANSIT→COMPLETED), plus
 * cancel (only from PENDING) and the state-machine guards that reject out-of-order
 * transitions with 409.
 */
class TransferIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var warehouseFactory: WarehouseFactory

    @Autowired private lateinit var productFactory: ProductFactory

    private lateinit var adminToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    private fun createLocation(warehouseId: UUID): UUID =
        UUID.fromString(
            post(
                "/api/warehouses/$warehouseId/locations",
                adminToken,
                mapOf("name" to "Loc-${UUID.randomUUID().toString().take(6)}"),
            ).bodyAsMap()!!["id"] as String,
        )

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

    /** Returns (fromLocationId, toLocationId, productId) with `onHand` seeded at the from-location. */
    private fun seedTransferContext(onHand: Int = 100): Triple<UUID, UUID, UUID> {
        val warehouseId = warehouseFactory.id(adminToken)
        val fromLocationId = createLocation(warehouseId)
        val toLocationId = createLocation(warehouseId)
        val productId = productFactory.id(adminToken)
        setStock(productId, warehouseId, fromLocationId, onHand)
        return Triple(fromLocationId, toLocationId, productId)
    }

    private fun createTransfer(
        fromLocationId: UUID,
        toLocationId: UUID,
        productId: UUID,
        qty: Int = 10,
    ): String {
        val result =
            post(
                "/api/inventory/transfers",
                adminToken,
                mapOf(
                    "fromLocationId" to fromLocationId,
                    "toLocationId" to toLocationId,
                    "productId" to productId,
                    "quantity" to qty,
                ),
            )
        assertEquals(201, result.status(), result.response.contentAsString)
        return result.bodyAsMap()!!["id"] as String
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list transfers without token returns 401`() {
        assertEquals(401, get("/api/inventory/transfers", null).status())
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    fun `create transfer returns 201 PENDING and appears in the list`() {
        val (from, to, product) = seedTransferContext()
        val id = createTransfer(from, to, product, qty = 10)

        val created = get("/api/inventory/transfers", adminToken).bodyAsList()!!
        val order = created.first { it["id"] == id }
        assertEquals("PENDING", order["status"])
        assertEquals(10, order["quantity"])
        assertNull(order["outMovementId"])
        assertNull(order["inMovementId"])
    }

    @Test
    fun `create transfer with insufficient on-hand returns 409`() {
        val (from, to, product) = seedTransferContext(onHand = 5)
        val result =
            post(
                "/api/inventory/transfers",
                adminToken,
                mapOf("fromLocationId" to from, "toLocationId" to to, "productId" to product, "quantity" to 50),
            )
        assertEquals(409, result.status())
    }

    @Test
    fun `create transfer from unknown location returns 404`() {
        val product = productFactory.id(adminToken)
        val result =
            post(
                "/api/inventory/transfers",
                adminToken,
                mapOf(
                    "fromLocationId" to UUID.randomUUID(),
                    "toLocationId" to UUID.randomUUID(),
                    "productId" to product,
                    "quantity" to 1,
                ),
            )
        assertEquals(404, result.status())
    }

    // ── Happy-path lifecycle ──────────────────────────────────────────────

    @Test
    fun `dispatch then receive walks the order to COMPLETED with both movements`() {
        val (from, to, product) = seedTransferContext()
        val id = createTransfer(from, to, product, qty = 25)

        val dispatched = post("/api/inventory/transfers/$id/dispatch", adminToken, null)
        assertEquals(200, dispatched.status(), dispatched.response.contentAsString)
        val dispatchedBody = dispatched.bodyAsMap()!!
        assertEquals("IN_TRANSIT", dispatchedBody["status"])
        assertNotNull(dispatchedBody["outMovementId"])

        val received = post("/api/inventory/transfers/$id/receive", adminToken, null)
        assertEquals(200, received.status(), received.response.contentAsString)
        val receivedBody = received.bodyAsMap()!!
        assertEquals("COMPLETED", receivedBody["status"])
        assertNotNull(receivedBody["inMovementId"])
    }

    @Test
    fun `dispatch moves stock from on-hand into in-transit at the source`() {
        val warehouseId = warehouseFactory.id(adminToken)
        val from = createLocation(warehouseId)
        val to = createLocation(warehouseId)
        val product = productFactory.id(adminToken)
        setStock(product, warehouseId, from, 100)
        val id = createTransfer(from, to, product, qty = 30)

        post("/api/inventory/transfers/$id/dispatch", adminToken, null)

        val entry =
            get(
                "/api/inventory/stock?productId=$product&warehouseId=$warehouseId&locationId=$from",
                adminToken,
            ).bodyAsList()!!.single()
        assertEquals(70, entry["onHand"])
        assertEquals(30, entry["inTransit"])
    }

    // ── State-machine guards ──────────────────────────────────────────────

    @Test
    fun `cancel a PENDING order returns 200 CANCELLED`() {
        val (from, to, product) = seedTransferContext()
        val id = createTransfer(from, to, product)
        val cancelled = post("/api/inventory/transfers/$id/cancel", adminToken, null)
        assertEquals(200, cancelled.status())
        assertEquals("CANCELLED", cancelled.bodyAsMap()!!["status"])
    }

    @Test
    fun `cannot cancel an order once dispatched`() {
        val (from, to, product) = seedTransferContext()
        val id = createTransfer(from, to, product)
        post("/api/inventory/transfers/$id/dispatch", adminToken, null)
        assertEquals(409, post("/api/inventory/transfers/$id/cancel", adminToken, null).status())
    }

    @Test
    fun `cannot receive an order that was never dispatched`() {
        val (from, to, product) = seedTransferContext()
        val id = createTransfer(from, to, product)
        assertEquals(409, post("/api/inventory/transfers/$id/receive", adminToken, null).status())
    }

    @Test
    fun `cannot dispatch an order twice`() {
        val (from, to, product) = seedTransferContext()
        val id = createTransfer(from, to, product)
        assertEquals(200, post("/api/inventory/transfers/$id/dispatch", adminToken, null).status())
        assertEquals(409, post("/api/inventory/transfers/$id/dispatch", adminToken, null).status())
    }

    @Test
    fun `dispatch an unknown order returns 404`() {
        assertEquals(404, post("/api/inventory/transfers/${UUID.randomUUID()}/dispatch", adminToken, null).status())
    }
}
