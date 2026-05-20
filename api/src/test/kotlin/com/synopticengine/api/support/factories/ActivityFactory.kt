package com.synopticengine.api.support.factories

import com.synopticengine.api.support.TestHttp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Creates activities via the public API. Defaults to `type=CALL` with a one-hour
 * window starting now, since most schedule-requiring types share that shape.
 * Pass `type = "NOTE"` (and no `scheduleFrom`) for note-shaped activities that
 * don't require a schedule.
 */
class ActivityFactory(
    private val http: TestHttp,
) {
    fun create(
        token: String,
        title: String = "Activity-${UUID.randomUUID().toString().take(8)}",
        type: String = "CALL",
        leadId: UUID? = null,
        personId: UUID? = null,
        organizationId: UUID? = null,
        userId: String? = null,
        scheduleFrom: Instant? = if (type == "NOTE") null else Instant.now(),
        scheduleTo: Instant? = if (type == "NOTE") null else scheduleFrom?.plus(1, ChronoUnit.HOURS),
        location: String? = null,
        comment: String? = null,
    ): Map<String, Any> {
        val body =
            buildMap<String, Any?> {
                put("title", title)
                put("type", type)
                if (leadId != null) put("leadId", leadId.toString())
                if (personId != null) put("personId", personId.toString())
                if (organizationId != null) put("organizationId", organizationId.toString())
                if (userId != null) put("userId", userId)
                if (scheduleFrom != null) put("scheduleFrom", scheduleFrom.toString())
                if (scheduleTo != null) put("scheduleTo", scheduleTo.toString())
                if (location != null) put("location", location)
                if (comment != null) put("comment", comment)
            }
        val result = http.post("/api/activities", token, body)
        return http.bodyAsMap(result)
            ?: error(
                "activity creation failed: status=${result.response.status} body=${result.response.contentAsString}",
            )
    }

    fun id(
        token: String,
        title: String = "Activity-${UUID.randomUUID().toString().take(8)}",
        type: String = "CALL",
    ): UUID = UUID.fromString(create(token, title = title, type = type)["id"] as String)
}
