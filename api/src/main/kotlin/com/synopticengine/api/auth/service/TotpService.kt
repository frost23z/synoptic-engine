package com.synopticengine.api.auth.service

import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP implementation — no external library.
 * Secrets are stored as Base32 strings (RFC 4648) because that is what
 * Google Authenticator and compatible apps expect in `otpauth://` URIs.
 */
@Service
class TotpService {
    fun generateSecret(): String {
        val bytes = ByteArray(20)
        secureRandom.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    fun buildQrUri(
        secret: String,
        userEmail: String,
        issuer: String = "Synoptic Engine",
    ): String {
        val label = URLEncoder.encode("$issuer:$userEmail", Charsets.UTF_8).replace("+", "%20")
        val enc = URLEncoder.encode(issuer, Charsets.UTF_8).replace("+", "%20")
        return "otpauth://totp/$label?secret=$secret&issuer=$enc&algorithm=SHA1&digits=6&period=30"
    }

    /**
     * Validates [code] (6-digit string) against [secret] using the current time window
     * plus/minus [windowSteps] (default 1) to tolerate clock skew of up to 30 s.
     */
    fun verify(
        secret: String,
        code: String,
        windowSteps: Int = 1,
    ): Boolean {
        val key = decodeBase32(secret)
        val counter = System.currentTimeMillis() / 1000L / PERIOD
        return (-windowSteps..windowSteps).any { offset -> computeHotp(key, counter + offset) == code }
    }

    fun computeCurrentCode(secret: String): String {
        val key = decodeBase32(secret)
        return computeHotp(key, System.currentTimeMillis() / 1000L / PERIOD)
    }

    // ── HOTP (RFC 4226) ───────────────────────────────────────────────────

    private fun computeHotp(
        key: ByteArray,
        counter: Long,
    ): String {
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0F
        val value =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return (value % DIGITS_POWER).toString().padStart(6, '0')
    }

    // ── RFC 4648 Base32 ───────────────────────────────────────────────────

    private fun encodeBase32(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(ALPHABET[(buffer ushr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) sb.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        return sb.toString()
    }

    private fun decodeBase32(input: String): ByteArray {
        val clean = input.uppercase().trimEnd('=')
        val out = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var idx = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "Invalid Base32 character: $c" }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out[idx++] = ((buffer ushr bitsLeft) and 0xFF).toByte()
            }
        }
        return out.copyOf(idx)
    }

    companion object {
        private const val PERIOD = 30L
        private const val DIGITS_POWER = 1_000_000
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        private val secureRandom = SecureRandom()
    }
}
