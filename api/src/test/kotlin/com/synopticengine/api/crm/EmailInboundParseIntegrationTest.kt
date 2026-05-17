package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class EmailInboundParseIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `POST mail inbound-parse creates email without auth`() {
        val result =
            post(
                "/api/mail/inbound-parse",
                null,
                mapOf(
                    "from" to "sender@example.com",
                    "to" to "inbox@synoptic.dev",
                    "subject" to "Test inbound",
                    "body" to "Hello",
                ),
            )
        assertEquals(201, result.status(), result.response.contentAsString)
    }

    @Test
    fun `GET mail attachments download returns 404 for unknown attachment`() {
        val token = adminToken()
        val result = get("/api/mail/attachments/${UUID.randomUUID()}/download", token)
        assertEquals(404, result.status())
    }
}
