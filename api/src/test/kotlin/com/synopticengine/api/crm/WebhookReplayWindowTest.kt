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
import kotlin.test.assertNotNull

/**
 * T3.3 — Unit tests for the 5-minute HMAC replay-window guard in
 * [InboundMailSignatureVerifier].
 *
 * These are pure unit tests (no Spring context, no Testcontainers) because
 * they only exercise the cryptographic logic and timestamp math.
 */
class WebhookReplayWindowTest {
    private val secret = "test-secret-key"
    private val verifier = InboundMailSignatureVerifier(secret, 300L)

    private val body = """{"event":"lead.created"}""".toByteArray(StandardCharsets.UTF_8)

    // ── acceptance cases ─────────────────────────────────────────────────────

    @Test
    fun `valid signature and fresh timestamp accepted`() {
        val ts = Instant.now().epochSecond
        val sig = sign(secret, ts, body)
        val req = request(sig, ts.toString())
        // should not throw
        verifier.verify(req, body)
    }

    @Test
    fun `timestamp at exactly the edge of the window is accepted`() {
        val ts = Instant.now().epochSecond - 300L
        val sig = sign(secret, ts, body)
        val req = request(sig, ts.toString())
        verifier.verify(req, body)
    }

    // ── rejection cases ──────────────────────────────────────────────────────

    @Test
    fun `request with no timestamp header is rejected`() {
        val ts = Instant.now().epochSecond
        val sig = sign(secret, ts, body)
        val req = MockHttpServletRequest()
        req.addHeader("X-Synoptic-Signature", sig)
        // no X-Synoptic-Timestamp
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `request with malformed timestamp header is rejected`() {
        val ts = Instant.now().epochSecond
        val sig = sign(secret, ts, body)
        val req = request(sig, "not-a-number")
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `replay beyond 5-minute window is rejected`() {
        // Timestamp is 6 minutes in the past — outside the 300-second window.
        val ts = Instant.now().epochSecond - 360L
        val sig = sign(secret, ts, body)
        val req = request(sig, ts.toString())
        val ex = assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
        assertNotNull(ex.message)
    }

    @Test
    fun `future timestamp beyond window is rejected`() {
        // A timestamp 6 minutes in the future (clock skew attack / forged timestamp).
        val ts = Instant.now().epochSecond + 360L
        val sig = sign(secret, ts, body)
        val req = request(sig, ts.toString())
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `request with wrong signature is rejected even with valid timestamp`() {
        val ts = Instant.now().epochSecond
        val req = request("deadbeefdeadbeef", ts.toString())
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `replay within window but with tampered timestamp fails signature check`() {
        // Attacker records a valid request, then modifies the timestamp header
        // to extend the window — the HMAC was computed over the original timestamp,
        // so the signature no longer matches.
        val originalTs = Instant.now().epochSecond - 250L
        val sig = sign(secret, originalTs, body)
        // Present the original signature but a fresh timestamp — HMAC mismatch.
        val req = request(sig, Instant.now().epochSecond.toString())
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `missing signature header is rejected`() {
        val ts = Instant.now().epochSecond
        val req = MockHttpServletRequest()
        req.addHeader("X-Synoptic-Timestamp", ts.toString())
        // no X-Synoptic-Signature
        assertFailsWith<AccessDeniedException> { verifier.verify(req, body) }
    }

    @Test
    fun `unconfigured secret rejects all requests`() {
        val unconfigured = InboundMailSignatureVerifier("", 300L)
        val ts = Instant.now().epochSecond
        val sig = sign(secret, ts, body)
        val req = request(sig, ts.toString())
        assertFailsWith<AccessDeniedException> { unconfigured.verify(req, body) }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private fun request(
        signature: String,
        timestamp: String,
    ): MockHttpServletRequest {
        val req = MockHttpServletRequest()
        req.addHeader("X-Synoptic-Signature", signature)
        req.addHeader("X-Synoptic-Timestamp", timestamp)
        return req
    }
}
