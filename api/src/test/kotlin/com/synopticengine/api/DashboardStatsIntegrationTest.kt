package com.synopticengine.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
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
    private lateinit var adminToken: String

    private val defaultPipelineId = "00000000-0000-0000-0000-000000000010"
    private val defaultStageId = "00000000-0000-0000-0000-000000000011"
    private val wonStageId = "00000000-0000-0000-0000-000000000015"

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    @Test
    fun `stats without token returns 401`() {
        assertEquals(401, get("/api/dashboard/stats?type=over-all", null).status())
    }

    @Test
    fun `stats with unknown type returns 400`() {
        assertEquals(400, get("/api/dashboard/stats?type=bogus", adminToken).status())
    }

    // ── over-all ──────────────────────────────────────────────────────────

    @Test
    fun `over-all returns the four period counters with deltas`() {
        val body = get("/api/dashboard/stats?type=over-all", adminToken).bodyAsMap()!!
        assertNotNull(body["leads"])
        assertNotNull(body["activities"])
        assertNotNull(body["quotes"])
        assertNotNull(body["persons"])
        @Suppress("UNCHECKED_CAST")
        val leads = body["leads"] as Map<String, Any>
        assertNotNull(leads["current"])
        assertNotNull(leads["previous"])
        assertNotNull(leads["delta"])
        assertNotNull(leads["changePercent"])
    }

    @Test
    fun `over-all current count grows after a new lead is created`() {
        val before =
            (
                get(
                    "/api/dashboard/stats?type=over-all",
                    adminToken,
                ).bodyAsMap()!!["leads"] as Map<*, *>
            )["current"] as Int
        post(
            "/api/leads",
            adminToken,
            mapOf(
                "title" to "Stats test ${UUID.randomUUID()}",
                "pipelineId" to defaultPipelineId,
                "stageId" to defaultStageId,
            ),
        )
        val after =
            (
                get(
                    "/api/dashboard/stats?type=over-all",
                    adminToken,
                ).bodyAsMap()!!["leads"] as Map<*, *>
            )["current"] as Int
        assertEquals(before + 1, after)
    }

    // ── revenue-stats ─────────────────────────────────────────────────────

    @Test
    fun `revenue-stats returns won and lost revenue fields`() {
        val body = get("/api/dashboard/stats?type=revenue-stats", adminToken).bodyAsMap()!!
        assertNotNull(body["wonRevenue"])
        assertNotNull(body["lostRevenue"])
        assertNotNull(body["previousWonRevenue"])
        assertNotNull(body["previousLostRevenue"])
    }

    @Test
    fun `revenue-stats won amount climbs after a won lead lands`() {
        val today = LocalDate.now().toString()
        val range = "startDate=${LocalDate.now().minusDays(7)}&endDate=$today"
        val before = get("/api/dashboard/stats?type=revenue-stats&$range", adminToken).bodyAsMap()!!
        val beforeWon = (before["wonRevenue"] as Number).toDouble()

        // Create a lead, move it to the WON stage.
        val createResp =
            post(
                "/api/leads",
                adminToken,
                mapOf(
                    "title" to "Won lead ${UUID.randomUUID()}",
                    "amount" to 1000,
                    "pipelineId" to defaultPipelineId,
                    "stageId" to defaultStageId,
                ),
            )
        val leadId = createResp.bodyAsMap()!!["id"] as String
        val moveResp = patch("/api/leads/$leadId/stage", adminToken, mapOf("stageId" to wonStageId))
        assertEquals(200, moveResp.status())

        val after = get("/api/dashboard/stats?type=revenue-stats&$range", adminToken).bodyAsMap()!!
        val afterWon = (after["wonRevenue"] as Number).toDouble()
        assertTrue(afterWon >= beforeWon + 1000.0, "expected won-revenue to climb by at least 1000")
    }

    // ── total-leads ───────────────────────────────────────────────────────

    @Test
    fun `total-leads returns time-series bucketed by day`() {
        val body = get("/api/dashboard/stats?type=total-leads", adminToken).bodyAsMap()!!
        assertEquals("day", body["bucket"])
        assertNotNull(body["series"])
        assertTrue(body["series"] is List<*>)
    }

    @Test
    fun `total-leads accepts week bucket`() {
        val body = get("/api/dashboard/stats?type=total-leads&bucket=week", adminToken).bodyAsMap()!!
        assertEquals("week", body["bucket"])
    }

    // ── revenue-by-sources / types ────────────────────────────────────────

    @Test
    fun `revenue-by-sources returns list`() {
        val result = get("/api/dashboard/stats?type=revenue-by-sources", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    @Test
    fun `revenue-by-types returns list`() {
        val result = get("/api/dashboard/stats?type=revenue-by-types", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    // ── top-selling-products / top-persons ────────────────────────────────

    @Test
    fun `top-selling-products returns list`() {
        val result = get("/api/dashboard/stats?type=top-selling-products", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    @Test
    fun `top-persons returns list`() {
        val result = get("/api/dashboard/stats?type=top-persons", adminToken)
        assertEquals(200, result.status())
        assertNotNull(result.bodyAsList())
    }

    // ── open-leads-by-states ──────────────────────────────────────────────

    @Test
    fun `open-leads-by-states returns one entry per stage that has open leads`() {
        // Seed a lead so we have at least one row.
        post(
            "/api/leads",
            adminToken,
            mapOf(
                "title" to "Open lead ${UUID.randomUUID()}",
                "pipelineId" to defaultPipelineId,
                "stageId" to defaultStageId,
            ),
        )
        val rows = get("/api/dashboard/stats?type=open-leads-by-states", adminToken).bodyAsList()!!
        assertTrue(rows.isNotEmpty())
        val row = rows.first { it["stageId"] == defaultStageId }
        assertTrue((row["count"] as Int) >= 1)
        assertNotNull(row["stageName"])
    }

    // ── Date range validation ─────────────────────────────────────────────

    @Test
    fun `bad date range returns 400`() {
        val result = get("/api/dashboard/stats?type=over-all&startDate=2026-12-01&endDate=2026-01-01", adminToken)
        assertEquals(400, result.status())
    }
}
