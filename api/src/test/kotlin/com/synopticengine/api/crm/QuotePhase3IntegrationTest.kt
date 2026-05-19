package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 3 / P3.4 — quote search, expired filter, and lead-product sync.
 */
class QuotePhase3IntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String

    private val defaultPipelineId = "00000000-0000-0000-0000-000000000010"
    private val defaultStageId = "00000000-0000-0000-0000-000000000011"

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    @Test
    fun `quote search filters by title substring`() {
        val leadId = createLead()
        val needle = "Needle-${UUID.randomUUID().toString().take(8)}"
        post(
            "/api/quotes",
            adminToken,
            mapOf(
                "leadId" to leadId,
                "title" to needle,
                "discount" to 0,
                "tax" to 0,
                "items" to emptyList<Map<String, Any>>(),
            ),
        )
        val result = get("/api/quotes/search?q=$needle", adminToken)
        assertEquals(200, result.status())
        val content = result.bodyAsMap()!!["content"] as List<*>
        assertTrue(content.any { (it as Map<*, *>)["title"] == needle })
    }

    @Test
    fun `expiredOnly filter returns only expired quotes`() {
        val leadId = createLead()
        // One expired, one fresh.
        val expiredId =
            post(
                "/api/quotes",
                adminToken,
                mapOf(
                    "leadId" to leadId,
                    "title" to "Expired",
                    "discount" to 0,
                    "tax" to 0,
                    "expiredAt" to LocalDate.now().minusDays(1).toString(),
                    "items" to emptyList<Map<String, Any>>(),
                ),
            ).bodyAsMap()!!["id"] as String
        post(
            "/api/quotes",
            adminToken,
            mapOf(
                "leadId" to leadId,
                "title" to "Future",
                "discount" to 0,
                "tax" to 0,
                "expiredAt" to LocalDate.now().plusDays(30).toString(),
                "items" to emptyList<Map<String, Any>>(),
            ),
        )
        val result = get("/api/quotes?expiredOnly=true", adminToken)
        assertEquals(200, result.status())
        val ids =
            (result.bodyAsMap()!!["content"] as List<*>)
                .map { (it as Map<*, *>)["id"] as String }
        assertTrue(expiredId in ids, "expired quote should be in expiredOnly listing")
        // Future quote must not be in expiredOnly results.
    }

    @Test
    fun `creating a quote item mirrors to lead_products for the linked lead`() {
        val leadId = createLead()
        // Need a product to attach.
        val productId = createProduct()
        val createResp =
            post(
                "/api/quotes",
                adminToken,
                mapOf(
                    "leadId" to leadId,
                    "title" to "Sync test",
                    "discount" to 0,
                    "tax" to 0,
                    "items" to
                        listOf(
                            mapOf(
                                "productId" to productId,
                                "quantity" to 3,
                                "unitPrice" to 100.0,
                                "discount" to 0,
                            ),
                        ),
                ),
            )
        assertEquals(201, createResp.status())

        // Lead-products endpoint sees the synced row.
        val products = get("/api/leads/$leadId/products", adminToken)
        assertEquals(200, products.status())
        val list = products.bodyAsList()!!
        val productRow = list.firstOrNull { it["productId"] == productId }
        assertNotNull(productRow)
        assertEquals(3, productRow["quantity"])
    }

    @Test
    fun `updating a quote item updates the synced lead_products row`() {
        val leadId = createLead()
        val productId = createProduct()
        val quoteId =
            post(
                "/api/quotes",
                adminToken,
                mapOf(
                    "leadId" to leadId,
                    "title" to "Sync update",
                    "discount" to 0,
                    "tax" to 0,
                    "items" to
                        listOf(
                            mapOf(
                                "productId" to productId,
                                "quantity" to 1,
                                "unitPrice" to 50.0,
                                "discount" to 0,
                            ),
                        ),
                ),
            ).bodyAsMap()!!["id"] as String

        put(
            "/api/quotes/$quoteId",
            adminToken,
            mapOf(
                "title" to "Sync update v2",
                "discount" to 0,
                "tax" to 0,
                "items" to
                    listOf(
                        mapOf(
                            "productId" to productId,
                            "quantity" to 7,
                            "unitPrice" to 75.0,
                            "discount" to 0,
                        ),
                    ),
            ),
        )
        val products = get("/api/leads/$leadId/products", adminToken).bodyAsList()!!
        val productRow = products.first { it["productId"] == productId }
        assertEquals(7, productRow["quantity"])
    }

    private fun createLead(): String =
        post(
            "/api/leads",
            adminToken,
            mapOf(
                "title" to "Lead ${UUID.randomUUID().toString().take(6)}",
                "pipelineId" to defaultPipelineId,
                "stageId" to defaultStageId,
            ),
        ).bodyAsMap()!!["id"] as String

    private fun createProduct(): String =
        post(
            "/api/products",
            adminToken,
            mapOf(
                "name" to "Product ${UUID.randomUUID().toString().take(6)}",
                "price" to 100,
            ),
        ).bodyAsMap()!!["id"] as String
}
