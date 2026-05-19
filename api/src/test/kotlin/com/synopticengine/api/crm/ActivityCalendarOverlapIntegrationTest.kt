package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 / P3.6 — calendar range + meeting overlap detection.
 */
class ActivityCalendarOverlapIntegrationTest : AbstractIntegrationTest() {
    private lateinit var adminToken: String
    private lateinit var ownerUserId: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        // Pick a real user id so the activity FK to users(id) is satisfied.
        val users = get("/api/users", adminToken).bodyAsList()!!
        ownerUserId = users.first()["id"] as String
    }

    @Test
    fun `calendar lists activities whose schedule intersects the range`() {
        val now = Instant.now()
        val inRangeId = createActivity("Inside range", now.plusSeconds(3600), now.plusSeconds(7200))
        createActivity("Outside range", now.plusSeconds(86400 * 10), now.plusSeconds(86400 * 11))

        val start = now.toString()
        val end = now.plusSeconds(86400).toString()
        val result = get("/api/activities/calendar?start=$start&end=$end", adminToken)
        assertEquals(200, result.status())
        val list = result.bodyAsList()!!
        assertTrue(list.any { it["id"] == inRangeId })
    }

    @Test
    fun `check-overlap finds a meeting that intersects the proposed window for the same user`() {
        val now = Instant.now()
        val existing = createMeeting("Existing meeting", now.plusSeconds(3600), now.plusSeconds(7200), ownerUserId)

        // Propose a meeting that overlaps and includes the same user.
        val resp =
            post(
                "/api/activities/check-overlap",
                adminToken,
                mapOf(
                    "scheduleFrom" to now.plusSeconds(5400).toString(),
                    "scheduleTo" to now.plusSeconds(9000).toString(),
                    "userIds" to listOf(ownerUserId),
                    "personIds" to emptyList<String>(),
                ),
            )
        assertEquals(200, resp.status())
        val body = resp.bodyAsMap()!!
        assertEquals(true, body["hasOverlap"])
        @Suppress("UNCHECKED_CAST")
        val overlaps = body["overlaps"] as List<Map<String, Any>>
        assertTrue(overlaps.any { it["id"] == existing })
    }

    @Test
    fun `check-overlap returns false when nobody overlaps`() {
        val now = Instant.now()
        // Random user id — guarantees no existing meeting matches.
        val randomUser = UUID.randomUUID().toString()
        val resp =
            post(
                "/api/activities/check-overlap",
                adminToken,
                mapOf(
                    "scheduleFrom" to now.plusSeconds(99999).toString(),
                    "scheduleTo" to now.plusSeconds(100999).toString(),
                    "userIds" to listOf(randomUser),
                    "personIds" to emptyList<String>(),
                ),
            )
        assertEquals(200, resp.status())
        assertEquals(false, resp.bodyAsMap()!!["hasOverlap"])
    }

    @Test
    fun `check-overlap excludes a specified activity`() {
        val now = Instant.now()
        val existing = createMeeting("Existing", now.plusSeconds(3600), now.plusSeconds(7200), ownerUserId)
        val resp =
            post(
                "/api/activities/check-overlap",
                adminToken,
                mapOf(
                    "scheduleFrom" to now.plusSeconds(3600).toString(),
                    "scheduleTo" to now.plusSeconds(7200).toString(),
                    "userIds" to listOf(ownerUserId),
                    "excludeActivityId" to existing,
                ),
            )
        assertEquals(200, resp.status())
        assertEquals(false, resp.bodyAsMap()!!["hasOverlap"])
    }

    @Test
    fun `bad range returns 400`() {
        val resp =
            post(
                "/api/activities/check-overlap",
                adminToken,
                mapOf(
                    "scheduleFrom" to "2026-12-01T00:00:00Z",
                    "scheduleTo" to "2026-01-01T00:00:00Z",
                    "userIds" to listOf(ownerUserId),
                ),
            )
        assertEquals(400, resp.status())
    }

    private fun createActivity(
        title: String,
        from: Instant,
        to: Instant,
    ): String =
        post(
            "/api/activities",
            adminToken,
            mapOf(
                "title" to title,
                "type" to "CALL",
                "scheduleFrom" to from.toString(),
                "scheduleTo" to to.toString(),
            ),
        ).bodyAsMap()!!["id"] as String

    private fun createMeeting(
        title: String,
        from: Instant,
        to: Instant,
        ownerUserId: String,
    ): String =
        post(
            "/api/activities",
            adminToken,
            mapOf(
                "title" to title,
                "type" to "MEETING",
                "scheduleFrom" to from.toString(),
                "scheduleTo" to to.toString(),
                "userId" to ownerUserId,
            ),
        ).bodyAsMap()!!["id"] as String
}
