package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import com.synopticengine.api.support.factories.ActivityFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals

class ActivityFileIntegrationTest : AbstractIntegrationTest() {
    @Autowired private lateinit var activityFactory: ActivityFactory

    @Test
    fun `upload file to activity returns 201`() {
        val token = adminToken()
        val activityId = activityFactory.id(token, type = "NOTE")
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
    fun `download unknown file from activity returns 404`() {
        val token = adminToken()
        val activityId = activityFactory.id(token, type = "NOTE")
        val result = get("/api/activities/$activityId/file/${UUID.randomUUID()}/download", token)
        assertEquals(404, result.status())
    }
}
