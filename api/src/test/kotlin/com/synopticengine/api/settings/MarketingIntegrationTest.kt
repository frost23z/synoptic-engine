package com.synopticengine.api.settings

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarketingIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list marketing events without token returns 401`() {
        assertEquals(401, get("/api/settings/marketing/events", null).status())
    }

    @Test
    fun `list marketing events as VIEWER returns 200`() {
        assertEquals(200, get("/api/settings/marketing/events", viewerToken).status())
    }

    @Test
    fun `create marketing event as VIEWER returns 403`() {
        assertEquals(403, post("/api/settings/marketing/events", viewerToken, mapOf("name" to "Event")).status())
    }

    @Test
    fun `list marketing campaigns without token returns 401`() {
        assertEquals(401, get("/api/settings/marketing/campaigns", null).status())
    }

    // ── Marketing Events ──────────────────────────────────────────────────

    @Test
    fun `create marketing event returns 201 with id and name`() {
        val body = createEvent()
        assertNotNull(body["id"])
        assertNotNull(body["name"])
    }

    @Test
    fun `create marketing event with eventDate persists date`() {
        val result =
            post(
                "/api/settings/marketing/events",
                adminToken,
                mapOf("name" to "Event ${UUID.randomUUID().toString().take(8)}", "eventDate" to "2026-01-15"),
            )
        assertEquals(201, result.status())
        assertEquals("2026-01-15", result.bodyAsMap()!!["eventDate"])
    }

    @Test
    fun `create marketing event with blank name returns 422`() {
        assertEquals(422, post("/api/settings/marketing/events", adminToken, mapOf("name" to "  ")).status())
    }

    @Test
    fun `get marketing event by id returns detail`() {
        val id = createEvent()["id"] as String
        val result = get("/api/settings/marketing/events/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get marketing event by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/marketing/events/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update marketing event returns 200`() {
        val id = createEvent()["id"] as String
        val result =
            put(
                "/api/settings/marketing/events/$id",
                adminToken,
                mapOf("name" to "Updated Event ${UUID.randomUUID().toString().take(8)}"),
            )
        assertEquals(200, result.status())
        assertTrue(result.bodyAsMap()!!["name"].toString().startsWith("Updated Event"))
    }

    @Test
    fun `update marketing event can set eventDate`() {
        val id = createEvent()["id"] as String
        val result =
            put(
                "/api/settings/marketing/events/$id",
                adminToken,
                mapOf("name" to "Updated Event ${UUID.randomUUID().toString().take(8)}", "eventDate" to "2026-02-20"),
            )
        assertEquals(200, result.status())
        assertEquals("2026-02-20", result.bodyAsMap()!!["eventDate"])
    }

    @Test
    fun `delete marketing event returns 204 and is unfindable`() {
        val id = createEvent()["id"] as String
        assertEquals(204, delete("/api/settings/marketing/events/$id", adminToken).status())
        assertEquals(404, get("/api/settings/marketing/events/$id", adminToken).status())
    }

    @Test
    fun `delete marketing event with active campaign returns 409`() {
        val eventId = createEvent()["id"] as String
        val campaignResult =
            post(
                "/api/settings/marketing/campaigns",
                adminToken,
                mapOf(
                    "name" to "campaign-${UUID.randomUUID().toString().take(8)}",
                    "subject" to "Campaign with event",
                    "eventId" to eventId,
                ),
            )
        assertEquals(201, campaignResult.status())

        val deleteResult = delete("/api/settings/marketing/events/$eventId", adminToken)
        assertEquals(409, deleteResult.status())
        assertEquals(eventId, get("/api/settings/marketing/events/$eventId", adminToken).bodyAsMap()!!["id"])
    }

    @Test
    fun `mass destroy marketing events returns 204 and removes them`() {
        val id1 = createEvent()["id"] as String
        val id2 = createEvent()["id"] as String
        assertEquals(
            204,
            post("/api/settings/marketing/events/mass-destroy", adminToken, mapOf("ids" to listOf(id1, id2))).status(),
        )
        assertEquals(404, get("/api/settings/marketing/events/$id1", adminToken).status())
    }

    // ── Marketing Campaigns ───────────────────────────────────────────────

    @Test
    fun `create marketing campaign returns 201 with subject`() {
        val body = createCampaign()
        assertNotNull(body["id"])
        assertNotNull(body["name"])
        assertEquals("Monthly Newsletter", body["subject"])
    }

    @Test
    fun `create marketing campaign with blank subject returns 422`() {
        assertEquals(
            422,
            post("/api/settings/marketing/campaigns", adminToken, mapOf("name" to "Camp", "subject" to "  ")).status(),
        )
    }

    @Test
    fun `get marketing campaign by id returns detail`() {
        val id = createCampaign()["id"] as String
        val result = get("/api/settings/marketing/campaigns/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get marketing campaign by unknown id returns 404`() {
        assertEquals(404, get("/api/settings/marketing/campaigns/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update marketing campaign returns 200 with new subject`() {
        val id = createCampaign()["id"] as String
        val result =
            put(
                "/api/settings/marketing/campaigns/$id",
                adminToken,
                mapOf("name" to "updated-${UUID.randomUUID().toString().take(4)}", "subject" to "Updated Subject"),
            )
        assertEquals(200, result.status())
        assertEquals("Updated Subject", result.bodyAsMap()!!["subject"])
    }

    @Test
    fun `delete marketing campaign returns 204 and is unfindable`() {
        val id = createCampaign()["id"] as String
        assertEquals(204, delete("/api/settings/marketing/campaigns/$id", adminToken).status())
        assertEquals(404, get("/api/settings/marketing/campaigns/$id", adminToken).status())
    }

    private fun createEvent(): Map<String, Any> =
        post(
            "/api/settings/marketing/events",
            adminToken,
            mapOf("name" to "Event ${UUID.randomUUID().toString().take(8)}", "description" to "Test event"),
        ).bodyAsMap()!!

    private fun createCampaign(): Map<String, Any> =
        post(
            "/api/settings/marketing/campaigns",
            adminToken,
            mapOf("name" to "campaign-${UUID.randomUUID().toString().take(8)}", "subject" to "Monthly Newsletter"),
        ).bodyAsMap()!!
}
