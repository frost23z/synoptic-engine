package com.synopticengine.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DashboardIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    // ── Auth guards ───────────────────────────────────────────────────────

    @Test
    fun `dashboard without token returns 401`() {
        assertEquals(401, get("/api/dashboard", null).status())
    }

    @Test
    fun `dashboard as VIEWER returns 200`() {
        assertEquals(200, get("/api/dashboard", viewerToken).status())
    }

    // ── Response structure ────────────────────────────────────────────────

    @Test
    fun `dashboard returns all required fields`() {
        val result = get("/api/dashboard", adminToken)
        assertEquals(200, result.status())
        val body = result.bodyAsMap()!!
        assertNotNull(body["totalLeads"])
        assertNotNull(body["openLeads"])
        assertNotNull(body["wonLeads"])
        assertNotNull(body["lostLeads"])
        assertNotNull(body["totalRevenue"])
        assertNotNull(body["leadsByStage"])
        assertNotNull(body["recentActivities"])
        assertNotNull(body["upcomingActivities"])
        assertNotNull(body["topSalespeople"])
    }

    @Test
    fun `dashboard counts reflect created leads`() {
        val before = get("/api/dashboard", adminToken).bodyAsMap()!!
        val beforeTotal = (before["totalLeads"] as Int)

        // Create a lead
        post(
            "/api/leads",
            adminToken,
            mapOf(
                "title" to "Dashboard Test Lead ${UUID.randomUUID().toString().take(6)}",
                "pipelineId" to "00000000-0000-0000-0000-000000000010",
                "stageId" to "00000000-0000-0000-0000-000000000011",
            ),
        )

        val after = get("/api/dashboard", adminToken).bodyAsMap()!!
        assertEquals(beforeTotal + 1, after["totalLeads"] as Int)
        assertTrue((after["openLeads"] as Int) >= 1)
    }

    @Test
    fun `dashboard recent activities reflect created activities`() {
        post(
            "/api/activities",
            adminToken,
            mapOf(
                "title" to "Dashboard Activity ${UUID.randomUUID().toString().take(6)}",
                "type" to "CALL",
                "scheduleFrom" to
                    java.time.Instant
                        .now()
                        .toString(),
                "scheduleTo" to
                    java.time.Instant
                        .now()
                        .plusSeconds(3600)
                        .toString(),
            ),
        )

        val result = get("/api/dashboard", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val recent = result.bodyAsMap()!!["recentActivities"] as List<*>
        assertTrue(recent.isNotEmpty())
    }

    @Test
    fun `dashboard upcoming activities only includes future undone activities`() {
        // Create a future activity
        post(
            "/api/activities",
            adminToken,
            mapOf(
                "title" to "Future Activity ${UUID.randomUUID().toString().take(6)}",
                "type" to "MEETING",
                "scheduleFrom" to
                    java.time.Instant
                        .now()
                        .plusSeconds(7200)
                        .toString(),
                "scheduleTo" to
                    java.time.Instant
                        .now()
                        .plusSeconds(10800)
                        .toString(),
            ),
        )

        val result = get("/api/dashboard", adminToken)
        assertEquals(200, result.status())
        @Suppress("UNCHECKED_CAST")
        val upcoming = result.bodyAsMap()!!["upcomingActivities"] as List<Map<String, Any>>
        // All upcoming activities must be not done
        assertTrue(upcoming.all { it["isDone"] == false })
    }

    @Test
    fun `dashboard leadsByStage is a list`() {
        val body = get("/api/dashboard", adminToken).bodyAsMap()!!
        assertTrue(body["leadsByStage"] is List<*>)
    }

    @Test
    fun `dashboard topSalespeople is a list`() {
        val body = get("/api/dashboard", adminToken).bodyAsMap()!!
        assertTrue(body["topSalespeople"] is List<*>)
    }
}
