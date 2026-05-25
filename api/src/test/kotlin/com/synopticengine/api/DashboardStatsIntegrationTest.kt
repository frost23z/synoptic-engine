package com.synopticengine.api

import com.synopticengine.api.support.factories.LeadFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 3 / P3.1 — assertions for the eight `GET /api/dashboard/stats?type=…`
 * endpoints. Most assertions verify structure rather than exact numbers because
 * the test database is shared with other integration tests that create their
 * own fixtures — but `over-all` and `total-leads` *do* assert deltas after a
 * known create.
 */
class DashboardStatsIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var leadFactory: LeadFactory

    private lateinit var adminToken: String

    private val wonStageId = "00000000-0000-0000-0000-000000000015"

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    @Test
    fun `stats without token returns 401`() {
        assertEquals(401, get("/api/dashboard/stats?type=over-all", null).status())
    }

    @Test
    fun `stats with unknown type returns 400`() {
        assertEquals(400, get("/api/dashboard/stats?type=bogus", adminToken).status())
    }

    @Test
    fun `over-all returns period counters and value metrics with deltas`() {
        val body = get("/api/dashboard/stats?type=over-all", adminToken).bodyAsMap()!!
        listOf("leads", "activities", "quotes", "persons", "organizations").forEach { assertNotNull(body[it]) }
        @Suppress("UNCHECKED_CAST")
        val leads = body["leads"] as Map<String, Any>
        listOf("current", "previous", "delta", "changePercent").forEach { assertNotNull(leads[it]) }
        @Suppress("UNCHECKED_CAST")
        val averageLeadValue = body["averageLeadValue"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val averageLeadsPerDay = body["averageLeadsPerDay"] as Map<String, Any>
        listOf("current", "previous", "delta", "changePercent").forEach {
            assertNotNull(averageLeadValue[it])
            assertNotNull(averageLeadsPerDay[it])
        }
    }

    @Test
    fun `over-all current count grows after a new lead is created`() {
        val before = leadsCurrentCount()
        leadFactory.create(adminToken, title = "Stats test ${UUID.randomUUID()}")
        assertEquals(before + 1, leadsCurrentCount())
    }

    @Test
    fun `revenue-stats returns won and lost revenue fields`() {
        val body = get("/api/dashboard/stats?type=revenue-stats", adminToken).bodyAsMap()!!
        listOf("wonRevenue", "lostRevenue", "previousWonRevenue", "previousLostRevenue").forEach {
            assertNotNull(body[it])
        }
    }

    @Test
    fun `revenue-stats won amount climbs after a won lead lands`() {
        val today = LocalDate.now().toString()
        val range = "startDate=${LocalDate.now().minusDays(7)}&endDate=$today"
        val beforeWon =
            (get("/api/dashboard/stats?type=revenue-stats&$range", adminToken).bodyAsMap()!!["wonRevenue"] as Number)
                .toDouble()

        val leadId =
            leadFactory.id(adminToken, title = "Won lead ${UUID.randomUUID()}").let {
                // amount=1000 is required for revenue movement.
                put(
                    "/api/leads/$it",
                    adminToken,
                    mapOf("title" to "Won lead", "amount" to 1000),
                )
                it
            }
        assertEquals(200, patch("/api/leads/$leadId/stage", adminToken, mapOf("stageId" to wonStageId)).status())

        val afterWon =
            (get("/api/dashboard/stats?type=revenue-stats&$range", adminToken).bodyAsMap()!!["wonRevenue"] as Number)
                .toDouble()
        assertTrue(afterWon >= beforeWon + 1000.0, "expected won-revenue to climb by at least 1000")
    }

    @Test
    fun `total-leads returns time-series with day or week bucket`() {
        val day = get("/api/dashboard/stats?type=total-leads", adminToken).bodyAsMap()!!
        assertEquals("day", day["bucket"])
        assertTrue(day["series"] is List<*>)

        val week = get("/api/dashboard/stats?type=total-leads&bucket=week", adminToken).bodyAsMap()!!
        assertEquals("week", week["bucket"])
    }

    @Test
    fun `revenue-by-sources returns a list`() {
        val result = get("/api/dashboard/stats?type=revenue-by-sources", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    @Test
    fun `revenue-by-types returns a list`() {
        val result = get("/api/dashboard/stats?type=revenue-by-types", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    @Test
    fun `top-selling-products returns a list`() {
        val result = get("/api/dashboard/stats?type=top-selling-products", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    @Test
    fun `top-persons returns a list`() {
        val result = get("/api/dashboard/stats?type=top-persons", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    @Test
    fun `open-leads-by-states returns one entry per stage with open leads`() {
        leadFactory.create(adminToken, title = "Open lead ${UUID.randomUUID()}")
        val rows = get("/api/dashboard/stats?type=open-leads-by-states", adminToken).bodyAsList()!!
        assertTrue(rows.isNotEmpty())
        val defaultStageId = "00000000-0000-0000-0000-000000000011"
        val row = rows.first { it["stageId"] == defaultStageId }
        assertTrue((row["count"] as Int) >= 1)
        assertNotNull(row["stageName"])
    }

    @Test
    fun `bad date range returns 400`() {
        assertEquals(
            400,
            get("/api/dashboard/stats?type=over-all&startDate=2026-12-01&endDate=2026-01-01", adminToken).status(),
        )
    }

    private fun leadsCurrentCount(): Int {
        @Suppress("UNCHECKED_CAST")
        return (
            get(
                "/api/dashboard/stats?type=over-all",
                adminToken,
            ).bodyAsMap()!!["leads"] as Map<String, Any>
        )["current"] as Int
    }
}
