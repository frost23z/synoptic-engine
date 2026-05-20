package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ActivityFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var activityFactory: ActivityFactory

    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `list activities without token returns 401`() {
        assertEquals(401, get("/api/activities", null).status())
    }

    @Test
    fun `list activities as VIEWER returns 200`() {
        assertEquals(200, get("/api/activities", viewerToken).status())
    }

    @Test
    fun `create activity as VIEWER returns 403`() {
        assertEquals(403, post("/api/activities", viewerToken, mapOf("title" to "x", "type" to "NOTE")).status())
    }

    @Test
    fun `delete activity as VIEWER returns 403`() {
        val id = activityFactory.id(adminToken)
        assertEquals(403, delete("/api/activities/$id", viewerToken).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create activity returns 201 with type CALL and schedule populated`() {
        val body = activityFactory.create(adminToken, comment = "Discuss Q3 targets")
        assertNotNull(body["id"])
        assertEquals("CALL", body["type"])
        assertEquals(false, body["isDone"])
        assertNotNull(body["scheduleFrom"])
        assertNotNull(body["scheduleTo"])
    }

    @Test
    fun `create activity with blank title returns 422`() {
        val request = mapOf("title" to "  ", "type" to "CALL", "scheduleFrom" to Instant.now().toString())
        assertEquals(422, post("/api/activities", adminToken, request).status())
    }

    @Test
    fun `create CALL activity without scheduleFrom returns 400`() {
        val request = mapOf("title" to "Test", "type" to "CALL", "scheduleTo" to Instant.now().toString())
        assertEquals(400, post("/api/activities", adminToken, request).status())
    }

    @Test
    fun `get activity by id returns full detail`() {
        val id = activityFactory.id(adminToken)
        val result = get("/api/activities/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id.toString(), result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get activity by unknown id returns 404`() {
        assertEquals(404, get("/api/activities/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update activity returns 200 with updated fields`() {
        val id = activityFactory.id(adminToken)
        val now = Instant.now()
        val update =
            mapOf(
                "title" to "Updated Call",
                "type" to "MEETING",
                "isDone" to true,
                "scheduleFrom" to now.toString(),
                "scheduleTo" to now.plusSeconds(3600).toString(),
            )
        val result = put("/api/activities/$id", adminToken, update)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertEquals("Updated Call", body["title"])
        assertEquals("MEETING", body["type"])
        assertEquals(true, body["isDone"])
    }

    @Test
    fun `toggle done flips isDone back and forth`() {
        val id = activityFactory.id(adminToken)
        assertEquals(false, get("/api/activities/$id", adminToken).bodyAsMap()!!["isDone"])

        val toggled = patch("/api/activities/$id/done", adminToken)
        assertEquals(200, toggled.status())
        assertEquals(true, toggled.bodyAsMap()!!["isDone"])

        val toggledBack = patch("/api/activities/$id/done", adminToken)
        assertEquals(false, toggledBack.bodyAsMap()!!["isDone"])
    }

    @Test
    fun `delete activity returns 204 and is unfindable`() {
        val id = activityFactory.id(adminToken)
        assertEquals(204, delete("/api/activities/$id", adminToken).status())
        assertEquals(404, get("/api/activities/$id", adminToken).status())
    }

    // ── Filtering ─────────────────────────────────────────────────────────

    @Test
    fun `filter activities by type returns only matching type`() {
        activityFactory.create(adminToken, type = "CALL")
        val result = get("/api/activities?type=CALL", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val items = (result.bodyAsMap()!!["content"] as List<Map<String, Any>>)
        assertTrue(items.all { it["type"] == "CALL" })
    }

    @Test
    fun `filter activities by isDone returns only done`() {
        val id = activityFactory.id(adminToken)
        patch("/api/activities/$id/done", adminToken)

        val result = get("/api/activities?isDone=true", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val items = (result.bodyAsMap()!!["content"] as List<Map<String, Any>>)
        assertTrue(items.all { it["isDone"] == true })
    }
}
