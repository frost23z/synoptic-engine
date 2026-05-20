package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.QuoteFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuoteIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var quoteFactory: QuoteFactory

    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list quotes without token returns 401`() {
        assertEquals(401, get("/api/quotes", null).status())
    }

    @Test
    fun `list quotes as VIEWER returns 200`() {
        assertEquals(200, get("/api/quotes", viewerToken).status())
    }

    @Test
    fun `create quote as VIEWER returns 403`() {
        val leadId = leadFactory.id(adminToken)
        assertEquals(
            403,
            post(
                "/api/quotes",
                viewerToken,
                mapOf(
                    "leadId" to leadId.toString(),
                    "title" to "x",
                    "items" to listOf(mapOf("quantity" to 1, "unitPrice" to 1.00, "discount" to 0)),
                ),
            ).status(),
        )
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create quote returns 201 with computed totals and draft status`() {
        val leadId = leadFactory.id(adminToken)
        val body =
            quoteFactory.create(
                adminToken,
                leadId = leadId,
                title = "Q-${UUID.randomUUID().toString().take(6)}",
                items =
                    listOf(
                        mapOf("quantity" to 2, "unitPrice" to 100.00, "discount" to 0),
                        mapOf("quantity" to 1, "unitPrice" to 50.00, "discount" to 20),
                    ),
                discount = 10,
                tax = 5,
            )
        assertNotNull(body["id"])
        assertEquals("draft", body["status"])
        assertEquals(leadId.toString(), body["leadId"])
        assertEquals(2, (body["items"] as List<*>).size)
        assertTrue((body["grandTotal"] as Number).toDouble() > 0)
    }

    @Test
    fun `create quote with blank title returns 422`() {
        val leadId = leadFactory.id(adminToken)
        assertEquals(
            422,
            post("/api/quotes", adminToken, mapOf("leadId" to leadId.toString(), "title" to "  ")).status(),
        )
    }

    @Test
    fun `get quote by id returns detail with items and totals`() {
        val id = quoteFactory.create(adminToken)["id"] as String
        val result = get("/api/quotes/$id", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(id, body["id"])
        assertNotNull(body["items"])
        assertNotNull(body["subTotal"])
        assertNotNull(body["grandTotal"])
    }

    @Test
    fun `get quote by unknown id returns 404`() {
        assertEquals(404, get("/api/quotes/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update quote replaces items and title`() {
        val id = quoteFactory.create(adminToken)["id"] as String
        val result =
            put(
                "/api/quotes/$id",
                adminToken,
                mapOf(
                    "title" to "Updated Quote",
                    "items" to listOf(mapOf("quantity" to 5, "unitPrice" to 200.00, "discount" to 0)),
                ),
            )
        assertEquals(200, result.status())
        assertEquals("Updated Quote", result.bodyAsMap()!!["title"])
        assertEquals(1, (result.bodyAsMap()!!["items"] as List<*>).size)
    }

    @Test
    fun `update quote status changes status`() {
        val id = quoteFactory.create(adminToken)["id"] as String
        val result = patch("/api/quotes/$id/status", adminToken, mapOf("status" to "SENT"))
        assertEquals(200, result.status())
        assertEquals("sent", result.bodyAsMap()!!["status"])
    }

    @Test
    fun `delete quote returns 204 and is unfindable`() {
        val id = quoteFactory.create(adminToken)["id"] as String
        assertEquals(204, delete("/api/quotes/$id", adminToken).status())
        assertEquals(404, get("/api/quotes/$id", adminToken).status())
    }

    @Test
    fun `mass destroy quotes returns 204 and removes them`() {
        val leadId = leadFactory.id(adminToken)
        val id1 = quoteFactory.create(adminToken, leadId = leadId)["id"] as String
        val id2 = quoteFactory.create(adminToken, leadId = leadId)["id"] as String
        assertEquals(204, post("/api/quotes/mass-destroy", adminToken, mapOf("ids" to listOf(id1, id2))).status())
        assertEquals(404, get("/api/quotes/$id1", adminToken).status())
        assertEquals(404, get("/api/quotes/$id2", adminToken).status())
    }

    @Test
    fun `filter quotes by leadId returns only that lead's quotes`() {
        val leadId = leadFactory.id(adminToken)
        quoteFactory.create(adminToken, leadId = leadId)

        val result = get("/api/quotes?leadId=$leadId", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(content.all { it["leadId"] == leadId.toString() })
    }

    @Test
    fun `filter quotes by status returns only that status`() {
        val id = quoteFactory.create(adminToken)["id"] as String
        patch("/api/quotes/$id/status", adminToken, mapOf("status" to "ACCEPTED"))

        val result = get("/api/quotes?status=ACCEPTED", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(content.isNotEmpty())
        assertTrue(content.all { it["status"] == "accepted" })
    }
}
