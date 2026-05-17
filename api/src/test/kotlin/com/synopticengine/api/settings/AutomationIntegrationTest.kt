package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutomationIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list workflows without token returns 401`() {
        assertEquals(401, get("/api/settings/workflows", null).status())
    }

    @Test
    fun `list workflows as VIEWER returns 200`() {
        assertEquals(200, get("/api/settings/workflows", viewerToken).status())
    }

    @Test
    fun `create workflow as VIEWER returns 403`() {
        assertEquals(
            403,
            post(
                "/api/settings/workflows",
                viewerToken,
                mapOf("name" to "wf", "eventName" to "lead.created"),
            ).status(),
        )
    }

    @Test
    fun `list webhooks without token returns 401`() {
        assertEquals(401, get("/api/settings/webhooks", null).status())
    }

    @Test
    fun `create webhook as VIEWER returns 403`() {
        assertEquals(
            403,
            post(
                "/api/settings/webhooks",
                viewerToken,
                mapOf("name" to "wh", "payloadUrl" to "https://example.com/hook"),
            ).status(),
        )
    }

    // ── Workflow CRUD ─────────────────────────────────────────────────────

    @Test
    fun `create workflow returns 201 with fields`() {
        val result = post("/api/settings/workflows", adminToken, validWorkflowRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("lead.created", body["eventName"])
        assertEquals(true, body["isActive"])
        assertTrue((body["conditions"] as List<*>).isEmpty())
        assertTrue((body["actions"] as List<*>).isEmpty())
    }

    @Test
    fun `create workflow with blank name returns 422`() {
        assertEquals(
            422,
            post(
                "/api/settings/workflows",
                adminToken,
                mapOf("name" to "  ", "eventName" to "lead.created"),
            ).status(),
        )
    }

    @Test
    fun `create workflow with conditions and actions`() {
        val request =
            mapOf(
                "name" to "Lead Created WF ${UUID.randomUUID().toString().take(6)}",
                "eventName" to "lead.created",
                "conditions" to listOf(mapOf("field" to "status", "operator" to "equals", "value" to "open")),
                "actions" to listOf(mapOf("type" to "LOG")),
                "isActive" to true,
            )
        val result = post("/api/settings/workflows", adminToken, request)
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertEquals(1, (body["conditions"] as List<*>).size)
        assertEquals(1, (body["actions"] as List<*>).size)
    }

    @Test
    fun `get workflow by id returns detail`() {
        val id = post("/api/settings/workflows", adminToken, validWorkflowRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/settings/workflows/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get workflow by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/workflows/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update workflow returns 200`() {
        val id = post("/api/settings/workflows", adminToken, validWorkflowRequest()).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/settings/workflows/$id",
                adminToken,
                mapOf("name" to "Updated WF", "eventName" to "lead.updated", "isActive" to false),
            )
        assertEquals(200, result.status())
        assertEquals("Updated WF", result.bodyAsMap()!!["name"])
        assertEquals("lead.updated", result.bodyAsMap()!!["eventName"])
        assertEquals(false, result.bodyAsMap()!!["isActive"])
    }

    @Test
    fun `delete workflow returns 204 and is unfindable`() {
        val id = post("/api/settings/workflows", adminToken, validWorkflowRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/settings/workflows/$id", adminToken).status())
        assertEquals(404, get("/api/settings/workflows/$id", adminToken).status())
    }

    // ── Webhook CRUD ──────────────────────────────────────────────────────

    @Test
    fun `create webhook returns 201 with fields`() {
        val result = post("/api/settings/webhooks", adminToken, validWebhookRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("https://example.com/hook", body["payloadUrl"])
        assertEquals(true, body["isActive"])
    }

    @Test
    fun `create webhook with blank payloadUrl returns 422`() {
        assertEquals(
            422,
            post(
                "/api/settings/webhooks",
                adminToken,
                mapOf("name" to "wh", "payloadUrl" to "  "),
            ).status(),
        )
    }

    @Test
    fun `create webhook with events list`() {
        val request =
            mapOf(
                "name" to "WH ${UUID.randomUUID().toString().take(6)}",
                "payloadUrl" to "https://example.com/hook",
                "events" to listOf("lead.created", "lead.updated"),
                "isActive" to true,
            )
        val result = post("/api/settings/webhooks", adminToken, request)
        assertEquals(201, result.status())
        val events = result.bodyAsMap()!!["events"] as List<*>
        assertEquals(2, events.size)
    }

    @Test
    fun `get webhook by id returns detail`() {
        val id = post("/api/settings/webhooks", adminToken, validWebhookRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/settings/webhooks/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get webhook by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/webhooks/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update webhook returns 200`() {
        val id = post("/api/settings/webhooks", adminToken, validWebhookRequest()).bodyAsMap()!!["id"] as String
        val result =
            put(
                "/api/settings/webhooks/$id",
                adminToken,
                mapOf(
                    "name" to "Updated WH",
                    "payloadUrl" to "https://updated.com/hook",
                    "events" to listOf("quote.created"),
                    "isActive" to false,
                ),
            )
        assertEquals(200, result.status())
        assertEquals("Updated WH", result.bodyAsMap()!!["name"])
        assertEquals("https://updated.com/hook", result.bodyAsMap()!!["payloadUrl"])
        assertEquals(false, result.bodyAsMap()!!["isActive"])
    }

    @Test
    fun `delete webhook returns 204 and is unfindable`() {
        val id = post("/api/settings/webhooks", adminToken, validWebhookRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/settings/webhooks/$id", adminToken).status())
        assertEquals(404, get("/api/settings/webhooks/$id", adminToken).status())
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validWorkflowRequest() =
        mapOf(
            "name" to "Workflow ${UUID.randomUUID().toString().take(8)}",
            "eventName" to "lead.created",
            "isActive" to true,
        )

    private fun validWebhookRequest() =
        mapOf(
            "name" to "Webhook ${UUID.randomUUID().toString().take(8)}",
            "payloadUrl" to "https://example.com/hook",
            "isActive" to true,
        )
}
