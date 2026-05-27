package com.synopticengine.api.crm.email.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies the `X-Synoptic-Signature` and `X-Synoptic-Timestamp` headers on
 * the public `/api/mail/inbound-parse` endpoint.
 *
 * The signature value is `hex(HMAC_SHA256(secret, "$timestamp.$rawBody"))` where
 * `$timestamp` is the Unix epoch seconds value sent in `X-Synoptic-Timestamp`.
 * Binding the signature to the timestamp means an attacker cannot reuse a valid
 * (signature, body) pair with a falsified timestamp.
 *
 * **T3.3 — Replay-window guard:** `X-Synoptic-Timestamp` must be present and
 * must satisfy `|now − timestamp| ≤ replayWindowSeconds` (default 300 s = 5 min).
 * Requests outside the window are rejected with 403, preventing indefinite
 * replay of a captured request.
 *
 * The secret comes from `synoptic.inbound-mail.webhook-secret`. If unset, every
 * request is refused — failing closed beats accepting unsigned payloads.
 *
 * **Sender requirements:** callers must include BOTH headers in each request.
 * For MTAs (Postmark / SendGrid), configure a custom request header
 * `X-Synoptic-Timestamp` with the current Unix epoch seconds and compute
 * `X-Synoptic-Signature` as described above.
 */
@Component
class InboundMailSignatureVerifier(
    @Value("\${synoptic.inbound-mail.webhook-secret:}") private val secret: String,
    @Value("\${synoptic.inbound-mail.replay-window-seconds:300}") private val replayWindowSeconds: Long,
) {
    private companion object {
        const val SIGNATURE_HEADER = "X-Synoptic-Signature"
        const val TIMESTAMP_HEADER = "X-Synoptic-Timestamp"
        const val ALGO = "HmacSHA256"
    }

    fun verify(
        request: HttpServletRequest,
        rawBody: ByteArray,
    ) {
        if (secret.isBlank()) {
            throw AccessDeniedException(
                "Inbound mail webhook secret not configured; refusing inbound delivery",
            )
        }

        // T3.3 — validate timestamp first; fast-fail on missing / stale requests
        // before spending CPU on HMAC computation.
        val timestampHeader = request.getHeader(TIMESTAMP_HEADER)
        if (timestampHeader.isNullOrBlank()) {
            throw AccessDeniedException("Missing $TIMESTAMP_HEADER header")
        }
        val timestampEpochSeconds =
            timestampHeader.toLongOrNull()
                ?: throw AccessDeniedException("$TIMESTAMP_HEADER is not a valid Unix epoch seconds value")
        val nowEpochSeconds = Instant.now().epochSecond
        val skewSeconds = Math.abs(nowEpochSeconds - timestampEpochSeconds)
        if (skewSeconds > replayWindowSeconds) {
            throw AccessDeniedException(
                "Request is outside the 5-minute replay window " +
                    "(skew=${skewSeconds}s, max=${replayWindowSeconds}s)",
            )
        }

        // Verify HMAC over "$timestamp.$rawBody" to bind the signature to the timestamp.
        val provided = request.getHeader(SIGNATURE_HEADER)
        if (provided.isNullOrBlank()) {
            throw AccessDeniedException("Missing $SIGNATURE_HEADER header")
        }
        val expected = hex(hmac(secret, timestampEpochSeconds, rawBody))
        if (!constantTimeEquals(expected, provided)) {
            throw AccessDeniedException("Invalid $SIGNATURE_HEADER")
        }
    }

    private fun hmac(
        secret: String,
        timestampEpochSeconds: Long,
        body: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(ALGO)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGO))
        // Prefix with "$timestamp." to bind the signature to the timestamp.
        val prefix = "$timestampEpochSeconds.".toByteArray(StandardCharsets.UTF_8)
        mac.update(prefix)
        return mac.doFinal(body)
    }

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b.toInt() and 0xff))
        return sb.toString()
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
