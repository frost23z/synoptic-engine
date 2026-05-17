package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ActivityFileIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `POST activities uploads file and returns 201`() {
        val token = adminToken()
        // Create an activity first
        val activityResult =
            post(
                "/api/activities",
                token,
                mapOf(
                    "title" to "File test activity",
                    "type" to "NOTE",
                    "scheduleFrom" to "2026-05-16T10:00:00Z",
                    "scheduleTo" to "2026-05-16T11:00:00Z",
                ),
            )
        assertEquals(201, activityResult.status(), activityResult.response.contentAsString)
        val activityId = activityResult.bodyAsMap()!!["id"] as String

        // Upload a file
        val result =
            multipart(
                "/api/activities/$activityId/file",
                token,
                "Hello, World!".toByteArray(),
                "test.txt",
            )
        assertEquals(201, result.status(), result.response.contentAsString)
    }

    @Test
    fun `GET activities file download returns 404 for unknown file`() {
        val token = adminToken()
        val activityResult =
            post(
                "/api/activities",
                token,
                mapOf(
                    "title" to "Download test",
                    "type" to "NOTE",
                    "scheduleFrom" to "2026-05-16T10:00:00Z",
                    "scheduleTo" to "2026-05-16T11:00:00Z",
                ),
            )
        val activityId = activityResult.bodyAsMap()!!["id"] as String
        val result = get("/api/activities/$activityId/file/${UUID.randomUUID()}/download", token)
        assertEquals(404, result.status())
    }
}
