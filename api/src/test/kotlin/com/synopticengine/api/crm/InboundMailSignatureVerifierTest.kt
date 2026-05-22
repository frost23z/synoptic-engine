package com.synopticengine.api.crm

import com.synopticengine.api.crm.email.web.InboundMailSignatureVerifier
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFailsWith

/**
 * Pure-logic test for the HMAC verifier — no Spring context, so it runs in milliseconds.
 * The integration test (`EmailInboundParseIntegrationTest`) only needs to prove the
 * verifier is wired into the controller; the verifier's own edge cases live here.
 */
class InboundMailSignatureVerifierTest {
    private val secret = "shared-secret"
    private val verifier = InboundMailSignatureVerifier(secret)
    private val body = """{"from":"a","to":"b"}""".toByteArray()

    @Test
    fun `valid signature passes`() {
        val req: HttpServletRequest =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", sign(secret, body))
            }
        verifier.verify(req, body) // no throw → success
    }

    @Test
    fun `missing header is rejected`() {
        assertFailsWith<AccessDeniedException> {
            verifier.verify(MockHttpServletRequest(), body)
        }
    }

    @Test
    fun `wrong signature is rejected`() {
        val req: HttpServletRequest =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", "deadbeef")
            }
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `signature from a different body is rejected (prevents replay against mutated payloads)`() {
        val req: HttpServletRequest =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", sign(secret, "tampered".toByteArray()))
            }
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `empty secret fails closed`() {
        val emptySecretVerifier = InboundMailSignatureVerifier(secret = "")
        val req: HttpServletRequest =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", sign(secret, body))
            }
        assertFailsWith<AccessDeniedException> { emptySecretVerifier.verify(req, body) }
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
