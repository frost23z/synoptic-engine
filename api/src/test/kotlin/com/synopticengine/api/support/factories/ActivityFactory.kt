package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Creates activities via the public API. Defaults to type=NOTE so the schedule
 * fields can be left blank — pass an explicit `type` (and `scheduleFrom`/`scheduleTo`)
 * to test the meeting/call paths.
 */
class ActivityFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        title: String = "Activity-${UUID.randomUUID().toString().take(8)}",
        type: String = "NOTE",
        leadId: UUID? = null,
        personId: UUID? = null,
        organizationId: UUID? = null,
        scheduleFrom: OffsetDateTime? = null,
        scheduleTo: OffsetDateTime? = null,
        location: String? = null,
    ): Map<String, Any> {
        val body =
            buildMap<String, Any?> {
                put("title", title)
                put("type", type)
                if (leadId != null) put("leadId", leadId.toString())
                if (personId != null) put("personId", personId.toString())
                if (organizationId != null) put("organizationId", organizationId.toString())
                if (scheduleFrom != null) put("scheduleFrom", scheduleFrom.toString())
                if (scheduleTo != null) put("scheduleTo", scheduleTo.toString())
                if (location != null) put("location", location)
            }
        val result = http.post("/api/activities", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "activity creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }
}
