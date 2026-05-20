package com.synopticengine.api.crm.email.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies the `X-Synoptic-Signature` header on the public `/api/mail/inbound-parse`
 * endpoint. The header is `hex(HMAC_SHA256(secret, raw_request_body))`, matching the
 * Postmark / SendGrid webhook pattern.
 *
 * The secret comes from `synoptic.inbound-mail.webhook-secret`. If unset, the verifier
 * refuses every request — failing closed beats accepting unsigned payloads.
 */
@Component
class InboundMailSignatureVerifier(
    @Value("\${synoptic.inbound-mail.webhook-secret:}") private val secret: String,
) {
    private companion object {
        const val HEADER = "X-Synoptic-Signature"
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
        val provided = request.getHeader(HEADER)
        if (provided.isNullOrBlank()) {
            throw AccessDeniedException("Missing $HEADER header")
        }
        val expected = hex(hmac(secret, rawBody))
        if (!constantTimeEquals(expected, provided)) {
            throw AccessDeniedException("Invalid $HEADER")
        }
    }

    private fun hmac(
        secret: String,
        body: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(ALGO)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGO))
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
