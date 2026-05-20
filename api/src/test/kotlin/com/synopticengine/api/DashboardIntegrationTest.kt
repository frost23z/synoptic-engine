package com.synopticengine.api

import com.synopticengine.api.support.factories.ActivityFactory
import com.synopticengine.api.support.factories.LeadFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DashboardIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var leadFactory: LeadFactory

    @Autowired private lateinit var activityFactory: ActivityFactory

    private lateinit var adminToken: String
    private lateinit var viewerToken: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        viewerToken = tokenFor(setOf("VIEWER"))
    }

    @Test
    fun `dashboard without token returns 401`() {
        assertEquals(401, get("/api/dashboard", null).status())
    }

    @Test
    fun `dashboard as VIEWER returns 200`() {
        assertEquals(200, get("/api/dashboard", viewerToken).status())
    }

    @Test
    fun `dashboard returns all required summary fields`() {
        val body = get("/api/dashboard", adminToken).bodyAsMap()!!
        listOf(
            "totalLeads",
            "openLeads",
            "wonLeads",
            "lostLeads",
            "totalRevenue",
            "leadsByStage",
            "recentActivities",
            "upcomingActivities",
            "topSalespeople",
        ).forEach { assertNotNull(body[it], "dashboard missing field $it") }
        assertTrue(body["leadsByStage"] is List<*>)
        assertTrue(body["topSalespeople"] is List<*>)
    }

    @Test
    fun `dashboard counts reflect created leads`() {
        val beforeTotal = get("/api/dashboard", adminToken).bodyAsMap()!!["totalLeads"] as Int
        leadFactory.create(adminToken, title = "Dashboard Test Lead")

        val after = get("/api/dashboard", adminToken).bodyAsMap()!!
        assertEquals(beforeTotal + 1, after["totalLeads"] as Int)
        assertTrue((after["openLeads"] as Int) >= 1)
    }

    @Test
    fun `dashboard recent activities reflect created activities`() {
        activityFactory.create(adminToken, title = "Dashboard Activity")
        @Suppress("UNCHECKED_CAST")
        val recent = get("/api/dashboard", adminToken).bodyAsMap()!!["recentActivities"] as List<*>
        assertTrue(recent.isNotEmpty())
    }

    @Test
    fun `dashboard upcoming activities are all not done`() {
        val now = Instant.now()
        activityFactory.create(
            adminToken,
            title = "Future Activity",
            type = "MEETING",
            scheduleFrom = now.plusSeconds(7200),
            scheduleTo = now.plusSeconds(10800),
        )
        @Suppress("UNCHECKED_CAST")
        val upcoming = get("/api/dashboard", adminToken).bodyAsMap()!!["upcomingActivities"] as List<Map<String, Any>>
        assertTrue(upcoming.all { it["isDone"] == false })
    }
}
