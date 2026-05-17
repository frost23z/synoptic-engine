package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EmailTagIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `POST mail compose email returns 201`() {
        val token = adminToken()
        val result =
            post(
                "/api/mail",
                token,
                mapOf(
                    "subject" to "Tag test",
                    "to" to "test@example.com",
                    "body" to "hello",
                ),
            )
        assertEquals(201, result.status(), result.response.contentAsString)
    }
}
