package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityIntegrationTest : AbstractIntegrationTest() {
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
        assertEquals(403, post("/api/activities", viewerToken, validCreateRequest()).status())
    }

    @Test
    fun `delete activity as VIEWER returns 403`() {
        val id = post("/api/activities", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(403, delete("/api/activities/$id", viewerToken).status())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Test
    fun `create activity returns 201 with correct fields`() {
        val result = post("/api/activities", adminToken, validCreateRequest())
        assertEquals(201, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["id"])
        assertEquals("CALL", body["type"])
        assertEquals(false, body["isDone"])
        assertNotNull(body["scheduleFrom"])
        assertNotNull(body["scheduleTo"])
    }

    @Test
    fun `create activity with blank title returns 422`() {
        val request = validCreateRequest().toMutableMap().also { it["title"] = "  " }
        assertEquals(422, post("/api/activities", adminToken, request).status())
    }

    @Test
    fun `create activity without scheduleFrom returns 400`() {
        val request = mapOf("title" to "Test", "type" to "CALL", "scheduleTo" to Instant.now().toString())
        assertEquals(400, post("/api/activities", adminToken, request).status())
    }

    @Test
    fun `get activity by id returns full detail`() {
        val id = post("/api/activities", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val result = get("/api/activities/$id", adminToken)
        assertEquals(200, result.status())
        assertEquals(id, result.bodyAsMap()!!["id"])
    }

    @Test
    fun `get activity by unknown id returns 404`() {
        assertEquals(404, get("/api/activities/${UUID.randomUUID()}", adminToken).status())
    }

    @Test
    fun `update activity returns 200`() {
        val id = post("/api/activities", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        val update =
            validCreateRequest().toMutableMap().also {
                it["title"] = "Updated Call"
                it["type"] = "MEETING"
                it["isDone"] = true
            }
        val result = put("/api/activities/$id", adminToken, update)
        assertEquals(200, result.status())
        assertEquals("Updated Call", result.bodyAsMap()!!["title"])
        assertEquals("MEETING", result.bodyAsMap()!!["type"])
        assertEquals(true, result.bodyAsMap()!!["isDone"])
    }

    @Test
    fun `toggle done flips isDone`() {
        val id = post("/api/activities", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(false, get("/api/activities/$id", adminToken).bodyAsMap()!!["isDone"])

        val toggled = patch("/api/activities/$id/done", adminToken)
        assertEquals(200, toggled.status())
        assertEquals(true, toggled.bodyAsMap()!!["isDone"])

        val toggledBack = patch("/api/activities/$id/done", adminToken)
        assertEquals(false, toggledBack.bodyAsMap()!!["isDone"])
    }

    @Test
    fun `delete activity returns 204 and is unfindable`() {
        val id = post("/api/activities", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        assertEquals(204, delete("/api/activities/$id", adminToken).status())
        assertEquals(404, get("/api/activities/$id", adminToken).status())
    }

    // ── Filtering ─────────────────────────────────────────────────────────

    @Test
    fun `filter activities by type`() {
        post("/api/activities", adminToken, validCreateRequest()) // type CALL
        val result = get("/api/activities?type=CALL", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val items = (result.bodyAsMap()!!["content"] as List<Map<String, Any>>)
        assertTrue(items.all { it["type"] == "CALL" })
    }

    @Test
    fun `filter activities by isDone`() {
        val id = post("/api/activities", adminToken, validCreateRequest()).bodyAsMap()!!["id"] as String
        patch("/api/activities/$id/done", adminToken) // mark done

        val result = get("/api/activities?isDone=true", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val items = (result.bodyAsMap()!!["content"] as List<Map<String, Any>>)
        assertTrue(items.all { it["isDone"] == true })
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun patch(
        path: String,
        token: String?,
    ) = mockMvc
        .perform(
            MockMvcRequestBuilders
                .patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .also { if (token != null) it.header("Authorization", "Bearer $token") },
        ).andReturn()

    private fun validCreateRequest(): Map<String, Any> {
        val now = Instant.now()
        return mapOf(
            "title" to "Follow up call ${UUID.randomUUID().toString().take(6)}",
            "type" to "CALL",
            "scheduleFrom" to now.toString(),
            "scheduleTo" to now.plus(1, ChronoUnit.HOURS).toString(),
            "comment" to "Discuss Q3 targets",
        )
    }
}
