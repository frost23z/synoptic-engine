package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

/**
 * The inbound-parse endpoint is public (no JWT) but signed: every request must include
 * `X-Synoptic-Signature` = `hex(HMAC_SHA256(secret, body))`. The shared secret is
 * configured via `synoptic.inbound-mail.webhook-secret` — `src/test/resources/application-test.yaml`
 * sets it to `test-inbound-mail-secret` for this suite (the `test` profile activated by
 * `AbstractIntegrationTest` overlays the main config).
 */
class EmailInboundParseIntegrationTest : AbstractIntegrationTest() {
    private val testSecret = "test-inbound-mail-secret"

    @Test
    fun `POST mail inbound-parse creates email when signed correctly`() {
        val body =
            objectMapper.writeValueAsBytes(
                mapOf(
                    "from" to "sender@example.com",
                    "to" to "inbox+00000000-0000-0000-0000-000000000001@synoptic.dev",
                    "subject" to "Test inbound",
                    "body" to "Hello",
                    "messageId" to "<m1@example.com>",
                    "references" to listOf("<parent@example.com>"),
                ),
            )
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/mail/inbound-parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Synoptic-Signature", sign(testSecret, body))
                        .content(body),
                ).andReturn()
        assertEquals(201, result.response.status, result.response.contentAsString)
        val json = objectMapper.readTree(result.response.contentAsString)
        assertEquals("<m1@example.com>", json["messageId"].asText())
        assertEquals("<parent@example.com>", json["referenceIds"][0].asText())
    }

    @Test
    fun `POST mail inbound-parse rejects signed payload with no tenant context in recipient`() {
        val body =
            objectMapper.writeValueAsBytes(
                mapOf(
                    "from" to "sender@example.com",
                    "to" to "inbox@synoptic.dev",
                    "subject" to "Test inbound",
                    "body" to "Hello",
                ),
            )
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/mail/inbound-parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Synoptic-Signature", sign(testSecret, body))
                        .content(body),
                ).andReturn()
        assertEquals(400, result.response.status, result.response.contentAsString)
    }

    @Test
    fun `POST mail inbound-parse rejects requests without a signature`() {
        val body =
            objectMapper.writeValueAsBytes(
                mapOf("from" to "x@y.z", "to" to "a@b.c", "subject" to "s", "body" to "b"),
            )
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/mail/inbound-parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andReturn()
        assertEquals(403, result.response.status)
    }

    @Test
    fun `POST mail inbound-parse rejects requests with a bogus signature`() {
        val body =
            objectMapper.writeValueAsBytes(
                mapOf("from" to "x@y.z", "to" to "a@b.c", "subject" to "s", "body" to "b"),
            )
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/mail/inbound-parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Synoptic-Signature", "deadbeef")
                        .content(body),
                ).andReturn()
        assertEquals(403, result.response.status)
    }

    private fun sign(
        secret: String,
        body: ByteArray,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
