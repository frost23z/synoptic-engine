package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ActivityFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 / P3.6 — calendar range + meeting overlap detection.
 */
class ActivityCalendarOverlapIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var activityFactory: ActivityFactory

    private lateinit var adminToken: String
    private lateinit var ownerUserId: String

    @BeforeEach
    fun setup() {
        adminToken = adminToken()
        // Pick a real user id so the activity FK to users(id) is satisfied.
        ownerUserId = get("/api/users", adminToken).bodyAsList()!!.first()["id"] as String
    }

    @Test
    fun `calendar lists activities whose schedule intersects the range`() {
        val now = Instant.now()
        val inRange =
            activityFactory.create(
                adminToken,
                title = "Inside range",
                type = "CALL",
                scheduleFrom = now.plusSeconds(3600),
                scheduleTo = now.plusSeconds(7200),
            )
        activityFactory.create(
            adminToken,
            title = "Outside range",
            type = "CALL",
            scheduleFrom = now.plusSeconds(86400 * 10),
            scheduleTo = now.plusSeconds(86400 * 11),
        )

        val result = get("/api/activities/calendar?start=$now&end=${now.plusSeconds(86400)}", adminToken)
        assertEquals(200, result.status())
        assertTrue(result.bodyAsList()!!.any { it["id"] == inRange["id"] })
    }

    @Test
    fun `check-overlap finds a meeting that intersects the proposed window for the same user`() {
        val now = uniqueBase()
        val existing =
            activityFactory.create(
                adminToken,
                title = "Existing meeting",
                type = "MEETING",
                scheduleFrom = now.plusSeconds(3600),
                scheduleTo = now.plusSeconds(7200),
                userId = ownerUserId,
            )

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
        assertTrue(overlaps.any { it["id"] == existing["id"] })
    }

    @Test
    fun `check-overlap returns false when nobody overlaps`() {
        val now = Instant.now()
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
        val now = uniqueBase()
        val existing =
            activityFactory.create(
                adminToken,
                title = "Existing",
                type = "MEETING",
                scheduleFrom = now.plusSeconds(3600),
                scheduleTo = now.plusSeconds(7200),
                userId = ownerUserId,
            )
        val resp =
            post(
                "/api/activities/check-overlap",
                adminToken,
                mapOf(
                    "scheduleFrom" to now.plusSeconds(3600).toString(),
                    "scheduleTo" to now.plusSeconds(7200).toString(),
                    "userIds" to listOf(ownerUserId),
                    "excludeActivityId" to existing["id"],
                ),
            )
        assertEquals(200, resp.status())
        assertEquals(false, resp.bodyAsMap()!!["hasOverlap"])
    }

    @Test
    fun `check-overlap with end before start returns 400`() {
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

    private fun uniqueBase(): Instant =
        Instant.now().plusSeconds((UUID.randomUUID().leastSignificantBits and Long.MAX_VALUE) % 86_400_000)
}
