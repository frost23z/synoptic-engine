package com.synopticengine.api.crm

import com.synopticengine.api.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

/**
 * Confirms that `/api/mail/inbound-parse` is wired through the signature verifier and
 * that the security filter allows the (signed) endpoint to reach the controller without
 * a JWT. Verifier-internal edge cases live in `InboundMailSignatureVerifierTest` — no
 * sense paying Spring boot to assert them again.
 */
@TestPropertySource(properties = ["synoptic.inbound-mail.webhook-secret=test-inbound-mail-secret"])
class EmailInboundParseIntegrationTest : AbstractIntegrationTest() {
    private val secret = "test-inbound-mail-secret"

    @Test
    fun `signed request creates an email and unsigned request is refused`() {
        val body =
            objectMapper.writeValueAsBytes(
                mapOf(
                    "from" to "sender@example.com",
                    "to" to "inbox@synoptic.dev",
                    "subject" to "Test inbound",
                    "body" to "Hello",
                ),
            )

        // Signed → 201 Created. Proves the controller route is reachable without auth
        // and that the verifier accepts a correctly-signed payload.
        val signed =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/mail/inbound-parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Synoptic-Signature", sign(secret, body))
                        .content(body),
                ).andReturn()
        assertEquals(201, signed.response.status, signed.response.contentAsString)

        // Unsigned → 403. Proves the verifier short-circuits before the service runs.
        val unsigned =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/mail/inbound-parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andReturn()
        assertEquals(403, unsigned.response.status)
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
