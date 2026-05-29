package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.AttributeFactory
import com.synopticengine.api.support.factories.LeadFactory
import com.synopticengine.api.support.factories.TagFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LeadIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var tagFactory: TagFactory

    @Autowired private lateinit var attributeFactory: AttributeFactory

    private lateinit var adminToken: String
    private lateinit var salespersonToken: String
    private lateinit var viewerToken: String

    // Fixed UUIDs from V009 seed.
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
        assertEquals(403, post("/api/leads", viewerToken, mapOf("title" to "x")).status())
    }

    @Test
    fun `delete lead as SALESPERSON returns 403`() {
        val id = leadFactory.id(adminToken)
        assertEquals(403, delete("/api/leads/$id", salespersonToken).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create lead returns 201 with seeded pipeline and open status`() {
        val body = leadFactory.create(adminToken)
        assertNotNull(body["id"])
        assertEquals("open", body["status"])
        assertEquals(defaultPipelineId, body["pipelineId"])
        assertEquals(defaultStageId, body["stageId"])
    }

    @Test
    fun `create lead with blank title returns 422`() {
        assertEquals(422, post("/api/leads", adminToken, mapOf("title" to " ")).status())
    }

    @Test
    fun `get lead by id returns full detail with tags`() {
        val id = leadFactory.id(adminToken)
        val result = get("/api/leads/$id", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(id.toString(), body["id"])
        assertNotNull(body["tags"])
    }

    @Test
    fun `get lead by unknown id returns 404`() {
        assertEquals(404, get("/api/leads/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update lead returns 200`() {
        val id = leadFactory.id(adminToken)
        val result = put("/api/leads/$id", adminToken, mapOf("title" to "Updated Lead", "amount" to 50000))
        assertEquals(200, result.status())
        assertEquals("Updated Lead", result.bodyAsMap()!!["title"])
    }

    @Test
    fun `delete lead returns 204 and is unfindable`() {
        val id = leadFactory.id(adminToken)
        assertEquals(204, delete("/api/leads/$id", adminToken).status())
        assertEquals(404, get("/api/leads/$id", adminToken).status())
    }

    // ── Stage movement ────────────────────────────────────────────────────

    @Test
    fun `move lead to won stage sets status to won and stamps closedAt`() {
        val id = leadFactory.id(adminToken)
        val result = patch("/api/leads/$id/stage", adminToken, mapOf("stageId" to wonStageId))
        assertEquals(200, result.status())
        assertEquals("won", result.bodyAsMap()!!["status"])
        assertNotNull(result.bodyAsMap()!!["closedAt"])
    }

    @Test
    fun `move lead to lost stage sets status to lost`() {
        val id = leadFactory.id(adminToken)
        val result =
            patch(
                "/api/leads/$id/stage",
                adminToken,
                mapOf("stageId" to lostStageId, "lostReason" to "Budget cut"),
            )
        assertEquals(200, result.status())
        assertEquals("lost", result.bodyAsMap()!!["status"])
    }

    // ── Tags ──────────────────────────────────────────────────────────────

    @Test
    fun `attach and detach tag on lead`() {
        val leadId = leadFactory.id(adminToken)
        val tagId = tagFactory.id(adminToken)

        val attached = post("/api/leads/$leadId/tags", adminToken, mapOf("tagId" to tagId.toString()))
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
    fun `kanban returns six seeded stages each with leads and totalAmount`() {
        leadFactory.create(adminToken)
        val result = get("/api/leads/kanban?pipelineId=$defaultPipelineId", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertEquals(6, body.size)
        body.forEach { group ->
            assertNotNull(group["stage"])
            assertNotNull(group["leads"])
            assertNotNull(group["totalAmount"])
        }
    }

    @Test
    fun `kanban lookup returns users, leadSources, leadTypes, and stages for pipeline`() {
        val result = get("/api/leads/kanban/lookup?pipelineId=$defaultPipelineId", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["users"])
        assertNotNull(body["leadSources"])
        assertNotNull(body["leadTypes"])
        val stages = body["stages"] as List<*>
        assertTrue(stages.isNotEmpty(), "stages list should not be empty for the default pipeline")
    }

    @Test
    fun `kanban lookup without token returns 401`() {
        assertEquals(401, get("/api/leads/kanban/lookup?pipelineId=$defaultPipelineId", null).status())
    }

    // ── Custom attribute partial update ───────────────────────────────────

    @Test
    fun `PATCH attributes sets value and returns attribute value list`() {
        val leadId = leadFactory.id(adminToken)
        val attrId = attributeFactory.id(adminToken, type = "TEXT", entityType = "Lead")
        val result =
            patch(
                "/api/leads/$leadId/attributes",
                adminToken,
                mapOf("attributeValues" to listOf(mapOf("attributeId" to attrId.toString(), "value" to "hello"))),
            )
        assertEquals(200, result.status())
        val body = result.bodyAsList()!!
        assertEquals(1, body.size)
        assertEquals("hello", body[0]["value"])
    }

    @Test
    fun `PATCH attributes on unknown lead returns 404`() {
        val attrId = attributeFactory.id(adminToken, type = "TEXT", entityType = "Lead")
        val result =
            patch(
                "/api/leads/${UUID.randomUUID()}/attributes",
                adminToken,
                mapOf("attributeValues" to listOf(mapOf("attributeId" to attrId.toString(), "value" to "x"))),
            )
        assertEquals(404, result.status())
    }

    @Test
    fun `PATCH attributes as VIEWER returns 403`() {
        val leadId = leadFactory.id(adminToken)
        val attrId = attributeFactory.id(adminToken, type = "TEXT", entityType = "Lead")
        assertEquals(
            403,
            patch(
                "/api/leads/$leadId/attributes",
                viewerToken,
                mapOf("attributeValues" to listOf(mapOf("attributeId" to attrId.toString(), "value" to "x"))),
            ).status(),
        )
    }

    // ── Search & filter ───────────────────────────────────────────────────

    @Test
    fun `search leads returns matches by title substring`() {
        val unique = "SRCH${UUID.randomUUID().toString().take(6)}"
        leadFactory.create(adminToken, title = "Lead $unique")
        val result = get("/api/leads/search?q=$unique", adminToken)
        assertEquals(200, result.status())
        assertTrue((result.bodyAsMap()!!["content"] as List<*>).isNotEmpty())
    }

    @Test
    fun `filter leads by pipeline and stage returns paginated content`() {
        leadFactory.create(adminToken)
        val result = get("/api/leads?pipelineId=$defaultPipelineId&stageId=$defaultStageId", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsMap()!!["content"])
    }

    // ── Mass operations ───────────────────────────────────────────────────

    @Test
    fun `mass destroy leads returns 204 and removes them`() {
        val id1 = leadFactory.id(adminToken)
        val id2 = leadFactory.id(adminToken)
        val result =
            post("/api/leads/mass-destroy", adminToken, mapOf("ids" to listOf(id1.toString(), id2.toString())))
        assertEquals(204, result.status())
        assertEquals(404, get("/api/leads/$id1", adminToken).status())
        assertEquals(404, get("/api/leads/$id2", adminToken).status())
    }

    // ── Lead sources and types ────────────────────────────────────────────

    @Test
    fun `create lead source returns 201 and appears in list`() {
        val name = "SRC-${UUID.randomUUID().toString().take(8)}"
        assertEquals(201, post("/api/lead-sources", adminToken, mapOf("name" to name)).status())
        val list = get("/api/lead-sources", adminToken).bodyAsList()!!
        assertTrue(list.any { it["name"] == name })
    }

    @Test
    fun `create lead type returns 201 and appears in list`() {
        val name = "TYP-${UUID.randomUUID().toString().take(8)}"
        assertEquals(201, post("/api/lead-types", adminToken, mapOf("name" to name)).status())
        val list = get("/api/lead-types", adminToken).bodyAsList()!!
        assertTrue(list.any { it["name"] == name })
    }
}
