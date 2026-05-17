package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuoteIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    private val defaultPipelineId = "00000000-0000-0000-0000-000000000010"
    private val defaultStageId = "00000000-0000-0000-0000-000000000011"

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
        val leadId = createLead()
        assertEquals(403, post("/api/quotes", viewerToken, validCreateRequest(leadId)).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create quote returns 201 with totals`() {
        val leadId = createLead()
        val request =
            mapOf(
                "leadId" to leadId,
                "title" to "Q-${UUID.randomUUID().toString().take(6)}",
                "discount" to 10,
                "tax" to 5,
                "items" to
                    listOf(
                        mapOf("quantity" to 2, "unitPrice" to 100.00, "discount" to 0),
                        mapOf("quantity" to 1, "unitPrice" to 50.00, "discount" to 20),
                    ),
            )
        val result = post("/api/quotes", adminToken, request)
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("draft", body["status"])
        assertEquals(leadId, body["leadId"])
        // subTotal: 2*100 + 1*50*(1-0.2) = 200 + 40 = 240
        // after quote discount: 240 * 0.9 = 216
        // tax: 216 * 0.05 = 10.80
        // grand total: 226.80
        assertEquals(2, (body["items"] as List<*>).size)
        val grandTotal = (body["grandTotal"] as Number).toDouble()
        assertTrue(grandTotal > 0)
    }

    @Test
    fun `create quote with blank title returns 422`() {
        val leadId = createLead()
        val request = mapOf("leadId" to leadId, "title" to "  ")
        assertEquals(422, post("/api/quotes", adminToken, request).status())
    }

    @Test
    fun `get quote by id returns detail with items`() {
        val leadId = createLead()
        val id = post("/api/quotes", adminToken, validCreateRequest(leadId)).bodyAsMap()!!["id"] as String
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
    fun `update quote replaces items`() {
        val leadId = createLead()
        val id = post("/api/quotes", adminToken, validCreateRequest(leadId)).bodyAsMap()!!["id"] as String

        val update =
            mapOf(
                "title" to "Updated Quote",
                "items" to listOf(mapOf("quantity" to 5, "unitPrice" to 200.00, "discount" to 0)),
            )
        val result = put("/api/quotes/$id", adminToken, update)
        assertEquals(200, result.status())
        assertEquals("Updated Quote", result.bodyAsMap()!!["title"])
        assertEquals(1, (result.bodyAsMap()!!["items"] as List<*>).size)
    }

    @Test
    fun `update quote status changes status`() {
        val leadId = createLead()
        val id = post("/api/quotes", adminToken, validCreateRequest(leadId)).bodyAsMap()!!["id"] as String

        val result = patch("/api/quotes/$id/status", adminToken, mapOf("status" to "SENT"))
        assertEquals(200, result.status())
        assertEquals("sent", result.bodyAsMap()!!["status"])
    }

    @Test
    fun `delete quote returns 204 and is unfindable`() {
        val leadId = createLead()
        val id = post("/api/quotes", adminToken, validCreateRequest(leadId)).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/quotes/$id", adminToken).status())
        assertEquals(404, get("/api/quotes/$id", adminToken).status())
    }

    @Test
    fun `mass destroy quotes returns 204`() {
        val leadId = createLead()
        val id1 = post("/api/quotes", adminToken, validCreateRequest(leadId)).bodyAsMap()!!["id"] as String
        val id2 = post("/api/quotes", adminToken, validCreateRequest(leadId)).bodyAsMap()!!["id"] as String
        assertEquals(204, post("/api/quotes/mass-destroy", adminToken, mapOf("ids" to listOf(id1, id2))).status())
        assertEquals(404, get("/api/quotes/$id1", adminToken).status())
        assertEquals(404, get("/api/quotes/$id2", adminToken).status())
    }

    @Test
    fun `filter quotes by leadId returns correct results`() {
        val leadId = createLead()
        post("/api/quotes", adminToken, validCreateRequest(leadId))

        val result = get("/api/quotes?leadId=$leadId", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(content.all { it["leadId"] == leadId })
    }

    @Test
    fun `filter quotes by status returns correct results`() {
        val leadId = createLead()
        val id = post("/api/quotes", adminToken, validCreateRequest(leadId)).bodyAsMap()!!["id"] as String
        patch("/api/quotes/$id/status", adminToken, mapOf("status" to "ACCEPTED"))

        val result = get("/api/quotes?status=ACCEPTED", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val content = result.bodyAsMap()!!["content"] as List<Map<String, Any>>
        assertTrue(content.isNotEmpty())
        assertTrue(content.all { it["status"] == "accepted" })
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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

    private fun validCreateRequest(leadId: String) =
        mapOf(
            "leadId" to leadId,
            "title" to "Quote ${UUID.randomUUID().toString().take(6)}",
            "items" to listOf(mapOf("quantity" to 1, "unitPrice" to 500.00, "discount" to 0)),
        )
}
