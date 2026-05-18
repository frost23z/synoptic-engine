package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P1.3 acceptance: an Activity can have a Person participant alongside User participants.
 * The legacy `POST /participants` endpoint still defaults to a user-typed participant for
 * back-compat with any caller that hasn't migrated yet.
 */
class ActivityPersonParticipantIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `person can be added and removed as an activity participant`() {
        val token = adminToken()

        val now = Instant.now()
        val activityId =
            post(
                "/api/activities",
                token,
                mapOf(
                    "title" to "Sales call ${UUID.randomUUID().toString().take(6)}",
                    "type" to "CALL",
                    "scheduleFrom" to now.toString(),
                    "scheduleTo" to now.plus(1, ChronoUnit.HOURS).toString(),
                ),
            ).bodyAsMap()!!["id"] as String

        val personId =
            post(
                "/api/contacts/persons",
                token,
                mapOf(
                    "firstName" to "Casey",
                    "lastName" to "Customer",
                    "email" to "casey-${UUID.randomUUID().toString().take(6)}@test.com",
                ),
            ).bodyAsMap()!!["id"] as String

        val attached =
            post(
                "/api/activities/$activityId/participants/persons",
                token,
                mapOf("personId" to personId),
            )
        assertEquals(
            200,
            attached.status(),
            "Expected 200, got ${attached.status()}: ${attached.response.contentAsString}",
        )
        @Suppress("UNCHECKED_CAST")
        val participants = attached.bodyAsMap()!!["participants"] as List<Map<String, Any?>>
        val match = participants.firstOrNull { it["personId"] == personId }
        assertNotNull(match, "the new participant should be in the response")
        assertNull(match["userId"], "person-typed participant must not also carry a userId")

        // participantPersonIds exposes the person ids for compatibility with newer readers.
        @Suppress("UNCHECKED_CAST")
        val personIds = attached.bodyAsMap()!!["participantPersonIds"] as List<String>
        assertTrue(personIds.contains(personId))

        // Detach via DELETE /participants/persons/{personId}.
        val detached = delete("/api/activities/$activityId/participants/persons/$personId", token)
        assertEquals(200, detached.status())
        @Suppress("UNCHECKED_CAST")
        val afterRemove = detached.bodyAsMap()!!["participants"] as List<Map<String, Any?>>
        assertTrue(afterRemove.none { it["personId"] == personId })
    }

    @Test
    fun `back-compat participants endpoint still attaches a user participant`() {
        val token = adminToken()
        val userId =
            (
                get("/api/users", token).bodyAsList()?.first()?.get("id") as String?
                    ?: error("seeded admin user should exist for this test")
            )

        val now = Instant.now()
        val activityId =
            post(
                "/api/activities",
                token,
                mapOf(
                    "title" to "Compat ${UUID.randomUUID().toString().take(6)}",
                    "type" to "MEETING",
                    "scheduleFrom" to now.toString(),
                    "scheduleTo" to now.plus(1, ChronoUnit.HOURS).toString(),
                ),
            ).bodyAsMap()!!["id"] as String

        val attached =
            post(
                "/api/activities/$activityId/participants",
                token,
                mapOf("userId" to userId),
            )
        assertEquals(200, attached.status())
        @Suppress("UNCHECKED_CAST")
        val ids = attached.bodyAsMap()!!["participantIds"] as List<String>
        assertTrue(ids.contains(userId), "back-compat shim should have added a user participant")

        // The new participants payload reflects the same row with userId set.
        @Suppress("UNCHECKED_CAST")
        val participants = attached.bodyAsMap()!!["participants"] as List<Map<String, Any?>>
        assertTrue(participants.any { it["userId"] == userId && it["personId"] == null })
    }
}
