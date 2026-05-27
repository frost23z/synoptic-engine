package com.synopticengine.api.crm

import com.synopticengine.api.crm.email.web.InboundMailSignatureVerifier
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFailsWith

/**
 * Pure-logic tests for the HMAC verifier — no Spring context, so it runs in milliseconds.
 *
 * The signing scheme changed in T3.3 (replay-window hardening): signatures are now
 * computed over `"$timestamp.$rawBody"` instead of `rawBody` alone, and requests
 * must include `X-Synoptic-Timestamp`. The companion test [WebhookReplayWindowTest]
 * covers the full window-boundary and replay-attack scenarios.
 */
class InboundMailSignatureVerifierTest {
    private val secret = "shared-secret"

    /** Use a generous window to avoid flakiness in CI. */
    private val verifier = InboundMailSignatureVerifier(secret, 300L)
    private val body = """{"from":"a","to":"b"}""".toByteArray()

    @Test
    fun `valid signature with fresh timestamp passes`() {
        val ts = Instant.now().epochSecond
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", sign(secret, ts, body))
                addHeader("X-Synoptic-Timestamp", ts.toString())
            }
        verifier.verify(req, body) // no throw → success
    }

    @Test
    fun `missing timestamp header is rejected`() {
        val ts = Instant.now().epochSecond
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", sign(secret, ts, body))
                // no X-Synoptic-Timestamp
            }
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `missing signature header is rejected`() {
        val ts = Instant.now().epochSecond
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Timestamp", ts.toString())
                // no X-Synoptic-Signature
            }
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `wrong signature is rejected`() {
        val ts = Instant.now().epochSecond
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", "deadbeef")
                addHeader("X-Synoptic-Timestamp", ts.toString())
            }
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `signature from a different body is rejected`() {
        val ts = Instant.now().epochSecond
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", sign(secret, ts, "tampered".toByteArray()))
                addHeader("X-Synoptic-Timestamp", ts.toString())
            }
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `empty secret fails closed regardless of headers`() {
        val emptyVerifier = InboundMailSignatureVerifier(secret = "", replayWindowSeconds = 300L)
        val ts = Instant.now().epochSecond
        val req =
            MockHttpServletRequest().apply {
                addHeader("X-Synoptic-Signature", sign(secret, ts, body))
                addHeader("X-Synoptic-Timestamp", ts.toString())
            }
        assertFailsWith<AccessDeniedException> { emptyVerifier.verify(req, body) }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private fun sign(
        secret: String,
        timestampEpochSeconds: Long,
        body: ByteArray,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val prefix = "$timestampEpochSeconds.".toByteArray(StandardCharsets.UTF_8)
        mac.update(prefix)
        val digest = mac.doFinal(body)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
