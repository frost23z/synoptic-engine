package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LeadIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var salespersonToken: String
    private lateinit var viewerToken: String

    // Fixed UUIDs from V009 seed
    private val defaultPipelineId = "00000000-0000-0000-0000-000000000010"
    private val defaultStageId = "00000000-0000-0000-0000-000000000011"
    private val wonStageId = "00000000-0000-0000-0000-000000000015"
    private val lostStageId = "00000000-0000-0000-0000-000000000016"

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        salespersonToken = salespersonToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list leads without token returns 401`() {
        assertEquals(401, get("/api/leads", null).status())
    }

    @Test
    fun `list leads as VIEWER returns 200`() {
        assertEquals(200, get("/api/leads", viewerToken).status())
    }

    @Test
    fun `create lead as VIEWER returns 403`() {
        assertEquals(403, post("/api/leads", viewerToken, validCreateRequest()).status())
    }

    @Test
    fun `delete lead as SALESPERSON returns 403`() {
        val id = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(403, delete("/api/leads/$id", salespersonToken).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create lead returns 201 with correct fields`() {
        val result = post("/api/leads", adminToken, validCreateRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("open", body["status"])
        assertEquals(defaultPipelineId, body["pipelineId"])
        assertEquals(defaultStageId, body["stageId"])
    }

    @Test
    fun `create lead with blank title returns 422`() {
        val request = mapOf("title" to " ", "pipelineId" to defaultPipelineId, "stageId" to defaultStageId)
        assertEquals(422, post("/api/leads", adminToken, request).status())
    }

    @Test
    fun `get lead by id returns full detail with tags`() {
        val id = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/leads/$id", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(id, body["id"])
        assertNotNull(body["tags"])
    }

    @Test
    fun `get lead by unknown id returns 404`() {
        assertEquals(404, get("/api/leads/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update lead returns 200`() {
        val id = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val update =
            mapOf(
                "title" to "Updated Lead",
                "amount" to 50000,
                "pipelineId" to defaultPipelineId,
                "stageId" to defaultStageId,
            )
        val result = put("/api/leads/$id", adminToken, update)
        assertEquals(200, result.status())
        assertEquals("Updated Lead", result.bodyAsMap()!!["title"])
    }

    @Test
    fun `delete lead returns 204 and is unfindable`() {
        val id = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/leads/$id", adminToken).status())
        assertEquals(404, get("/api/leads/$id", adminToken).status())
    }

    // ── Stage movement ────────────────────────────────────────────────────

    @Test
    fun `move lead to won stage sets status to won`() {
        val id = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = patch("/api/leads/$id/stage", adminToken, mapOf("stageId" to wonStageId))
        assertEquals(200, result.status())
        assertEquals("won", result.bodyAsMap()!!["status"])
        assertNotNull(result.bodyAsMap()!!["closedAt"])
    }

    @Test
    fun `move lead to lost stage sets status to lost`() {
        val id = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result =
            patch(
                "/api/leads/$id/stage",
                adminToken,
                mapOf(
                    "stageId" to lostStageId,
                    "lostReason" to "Budget cut",
                ),
            )
        assertEquals(200, result.status())
        assertEquals("lost", result.bodyAsMap()!!["status"])
    }

    // ── Tags ──────────────────────────────────────────────────────────────

    @Test
    fun `attach and detach tag on lead`() {
        val leadId = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val tagId =
            post(
                "/api/tags",
                adminToken,
                mapOf("name" to "LT-${UUID.randomUUID().toString().take(8)}"),
            ).bodyAsMap()!!["id"] as String

        val attached = post("/api/leads/$leadId/tags", adminToken, mapOf("tagId" to tagId))
        assertEquals(200, attached.status())
        @Suppress("UNCHECKED_CAST")
        assertEquals(1, (attached.bodyAsMap()!!["tags"] as List<*>).size)

        val detached = delete("/api/leads/$leadId/tags/$tagId", adminToken)
        assertEquals(200, detached.status())
        @Suppress("UNCHECKED_CAST")
        assertTrue((detached.bodyAsMap()!!["tags"] as List<*>).isEmpty())
    }

    // ── Kanban ────────────────────────────────────────────────────────────

    @Test
    fun `kanban returns stages grouped with leads`() {
        post("/api/leads", adminToken, validCreateRequest())
        val result = get("/api/leads/kanban?pipelineId=$defaultPipelineId", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertEquals(6, body.size) // 6 seeded stages
        body.forEach { group ->
            assertNotNull(group["stage"])
            assertNotNull(group["leads"])
            assertNotNull(group["totalAmount"])
        }
    }

    // ── Search ────────────────────────────────────────────────────────────

    @Test
    fun `search leads returns matching results`() {
        val unique = "SRCH${UUID.randomUUID().toString().take(6)}"
        post(
            "/api/leads",
            adminToken,
            mapOf(
                "title" to "Lead $unique",
                "pipelineId" to defaultPipelineId,
                "stageId" to defaultStageId,
            ),
        )
        val result = get("/api/leads/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        val content = result.bodyAsMap()!!["content"] as List<*>
        assertTrue(content.isNotEmpty())
    }

    // ── Mass operations ───────────────────────────────────────────────────

    @Test
    fun `mass destroy leads returns 204`() {
        val id1 = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val id2 = post("/api/leads", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = post("/api/leads/mass-destroy", adminToken, mapOf("ids" to listOf(id1, id2)))
        assertEquals(204, result.status())
        assertEquals(404, get("/api/leads/$id1", adminToken).status())
        assertEquals(404, get("/api/leads/$id2", adminToken).status())
    }

    @Test
    fun `filter leads by pipeline and stage`() {
        post("/api/leads", adminToken, validCreateRequest())
        val result = get("/api/leads?pipelineId=$defaultPipelineId&stageId=$defaultStageId", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsMap()!!["content"])
    }

    // ── Lead sources and types ────────────────────────────────────────────

    @Test
    fun `list lead sources returns seeded sources`() {
        val result = get("/api/lead-sources", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.bodyAsList()!!.isNotEmpty())
    }

    @Test
    fun `create lead source returns 201`() {
        val result =
            post("/api/lead-sources", adminToken, mapOf("name" to "SRC-${UUID.randomUUID().toString().take(8)}"))
        assertEquals(201, result.status())
    }

    @Test
    fun `list lead types returns seeded types`() {
        val result = get("/api/lead-types", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.bodyAsList()!!.isNotEmpty())
    }

    @Test
    fun `create lead type returns 201`() {
        val result = post("/api/lead-types", adminToken, mapOf("name" to "TYP-${UUID.randomUUID().toString().take(8)}"))
        assertEquals(201, result.status())
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validCreateRequest() =
        mapOf(
            "title" to "Lead ${UUID.randomUUID().toString().take(8)}",
            "pipelineId" to defaultPipelineId,
            "stageId" to defaultStageId,
        )
}
