package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ActivityFactory
import com.synopticengine.api.support.factories.TenantProvisioner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Regression test for the meeting-overlap query that previously read across
 * every tenant in the database. `ActivityRepository.findOverlappingMeetings`
 * is a native query and the `activities` table was not RLS-protected, so a
 * user in Tenant B asking "is anyone overlapping this slot for user X?" with
 * X belonging to Tenant A would return Tenant A's meeting — leaking both the
 * existence of the meeting and its scheduled time.
 *
 * V011 enables RLS on `activities` (with the visibility-aware policy for the
 * 'leads.activities' ResourceType) and the previous PR added the
 * `tenant_id = :tenantId` parameter to the native query. This test asserts
 * the combined behaviour.
 */
class ActivityOverlapTenantIsolationIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var tenantProvisioner: TenantProvisioner

    @Autowired private lateinit var activityFactory: ActivityFactory

    @Test
    fun `check-overlap does not surface another tenant's meeting even when the user id matches`() {
        val a = tenantProvisioner.provision("overlap-iso-a")
        val b = tenantProvisioner.provision("overlap-iso-b")

        val aUserId = get("/api/users", a.token).bodyAsList()!!.first()["id"] as String

        // Tenant A puts a meeting on its admin's calendar.
        val window = Instant.now().plusSeconds(3600)
        activityFactory.create(
            a.token,
            title = "Tenant A board meeting",
            type = "MEETING",
            scheduleFrom = window,
            scheduleTo = window.plusSeconds(3600),
            userId = aUserId,
        )

        // Tenant B asks "is `aUserId` busy in this slot?" — they wouldn't normally
        // know A's user id, but if they ever guessed it (UUID collision is
        // astronomically unlikely; the test simulates the leak directly).
        val resp =
            post(
                "/api/activities/check-overlap",
                b.token,
                mapOf(
                    "scheduleFrom" to window.plusSeconds(900).toString(),
                    "scheduleTo" to window.plusSeconds(2700).toString(),
                    "userIds" to listOf(aUserId),
                    "personIds" to emptyList<String>(),
                ),
            )
        assertEquals(200, resp.status())
        // Before the fix this returned the A-tenant meeting and `hasOverlap` was true.
        assertEquals(
            false,
            resp.bodyAsMap()!!["hasOverlap"],
            "Tenant B must not see Tenant A's meeting in the overlap result",
        )
    }
}
