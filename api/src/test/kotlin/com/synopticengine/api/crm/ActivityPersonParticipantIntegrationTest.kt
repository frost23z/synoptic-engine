package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ActivityFactory
import com.synopticengine.api.support.factories.PersonFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
    @Autowired private lateinit var activityFactory: ActivityFactory

    @Autowired private lateinit var personFactory: PersonFactory

    @Test
    fun `person can be added and removed as an activity participant`() {
        val token = adminToken()
        val activityId = activityFactory.id(token)
        val personId = personFactory.id(token, firstName = "Casey", lastName = "Customer")

        val attached =
            post(
                "/api/activities/$activityId/participants/persons",
                token,
                mapOf("personId" to personId.toString()),
            )
        assertEquals(200, attached.status(), attached.response.contentAsString)
        @Suppress("UNCHECKED_CAST")
        val participants = attached.bodyAsMap()!!["participants"] as List<Map<String, Any?>>
        val match = participants.firstOrNull { it["personId"] == personId.toString() }
        assertNotNull(match, "the new participant should be in the response")
        assertNull(match["userId"], "person-typed participant must not also carry a userId")

        @Suppress("UNCHECKED_CAST")
        val personIds = attached.bodyAsMap()!!["participantPersonIds"] as List<String>
        assertTrue(personIds.contains(personId.toString()))

        val detached = delete("/api/activities/$activityId/participants/persons/$personId", token)
        assertEquals(200, detached.status())
        @Suppress("UNCHECKED_CAST")
        val afterRemove = detached.bodyAsMap()!!["participants"] as List<Map<String, Any?>>
        assertTrue(afterRemove.none { it["personId"] == personId.toString() })
    }

    @Test
    fun `back-compat POST participants attaches a user participant`() {
        val token = adminToken()
        val userId = get("/api/users", token).bodyAsList()!!.first()["id"] as String
        val activityId = activityFactory.id(token, type = "MEETING")

        val attached = post("/api/activities/$activityId/participants", token, mapOf("userId" to userId))
        assertEquals(200, attached.status())
        @Suppress("UNCHECKED_CAST")
        val ids = attached.bodyAsMap()!!["participantIds"] as List<String>
        assertTrue(ids.contains(userId), "back-compat shim should have added a user participant")

        @Suppress("UNCHECKED_CAST")
        val participants = attached.bodyAsMap()!!["participants"] as List<Map<String, Any?>>
        assertTrue(participants.any { it["userId"] == userId && it["personId"] == null })
    }
}
