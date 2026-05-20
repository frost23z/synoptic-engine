package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.ProductFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 3 / P3.4 — quote search, expired filter, and lead-product sync.
 */
class QuotePhase3IntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var productFactory: ProductFactory

    private lateinit var adminToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    @Test
    fun `quote search filters by title substring`() {
        val leadId = leadFactory.id(adminToken)
        val needle = "Needle-${UUID.randomUUID().toString().take(8)}"
        post(
            "/api/quotes",
            adminToken,
            mapOf(
                "leadId" to leadId.toString(),
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
        val leadId = leadFactory.id(adminToken)
        val expiredId = createQuoteWithExpiry(leadId, "Expired", LocalDate.now().minusDays(1))
        createQuoteWithExpiry(leadId, "Future", LocalDate.now().plusDays(30))

        val result = get("/api/quotes?expiredOnly=true", adminToken)
        assertEquals(200, result.status())
        val ids = (result.bodyAsMap()!!["content"] as List<*>).map { (it as Map<*, *>)["id"] as String }
        assertTrue(expiredId in ids, "expired quote should be in expiredOnly listing")
    }

    @Test
    fun `creating a quote item mirrors to lead_products for the linked lead`() {
        val leadId = leadFactory.id(adminToken)
        val productId = productFactory.id(adminToken)
        val createResp =
            post(
                "/api/quotes",
                adminToken,
                mapOf(
                    "leadId" to leadId.toString(),
                    "title" to "Sync test",
                    "discount" to 0,
                    "tax" to 0,
                    "items" to
                        listOf(
                            mapOf(
                                "productId" to productId.toString(),
                                "quantity" to 3,
                                "unitPrice" to 100.0,
                                "discount" to 0,
                            ),
                        ),
                ),
            )
        assertEquals(201, createResp.status())

        val productRow =
            get("/api/leads/$leadId/products", adminToken)
                .bodyAsList()!!
                .firstOrNull { it["productId"] == productId.toString() }
        assertNotNull(productRow)
        assertEquals(3, productRow["quantity"])
    }

    @Test
    fun `updating a quote item updates the synced lead_products row`() {
        val leadId = leadFactory.id(adminToken)
        val productId = productFactory.id(adminToken)
        val quoteId =
            post(
                "/api/quotes",
                adminToken,
                mapOf(
                    "leadId" to leadId.toString(),
                    "title" to "Sync update",
                    "discount" to 0,
                    "tax" to 0,
                    "items" to
                        listOf(
                            mapOf(
                                "productId" to productId.toString(),
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
                            "productId" to productId.toString(),
                            "quantity" to 7,
                            "unitPrice" to 75.0,
                            "discount" to 0,
                        ),
                    ),
            ),
        )
        val productRow =
            get("/api/leads/$leadId/products", adminToken)
                .bodyAsList()!!
                .first { it["productId"] == productId.toString() }
        assertEquals(7, productRow["quantity"])
    }

    private fun createQuoteWithExpiry(
        leadId: UUID,
        title: String,
        expiry: LocalDate,
    ): String =
        post(
            "/api/quotes",
            adminToken,
            mapOf(
                "leadId" to leadId.toString(),
                "title" to title,
                "discount" to 0,
                "tax" to 0,
                "expiredAt" to expiry.toString(),
                "items" to emptyList<Map<String, Any>>(),
            ),
        ).bodyAsMap()!!["id"] as String
}
