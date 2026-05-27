package com.synopticengine.api.auth

import com.synopticengine.api.auth.config.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T7.4 — Pure unit tests for [JwtTokenProvider].
 *
 * No Spring context, no Testcontainers. Run via `./gradlew unitTests`.
 *
 * A 512-bit (64-byte) HMAC-SHA key is used; jjwt 0.13 requires at least 256 bits
 * for HS256. The secret is generated inline — these tests are entirely hermetic.
 */
class JwtTokenProviderUnitTest {
    // 64-char ASCII string → 64-byte key; satisfies jjwt's minimum for HS256.
    private val secret = "abcdefghijklmnopqrstuvwxyz012345abcdefghijklmnopqrstuvwxyz012345"
    private val accessExpiry = 15L * 60 * 1000       // 15 min in ms
    private val refreshExpiry = 7L * 24 * 60 * 60 * 1000 // 7 days in ms

    private fun provider() = JwtTokenProvider(secret, accessExpiry, refreshExpiry)

    private fun principal(
        id: UUID = UUID.randomUUID(),
        tenantId: UUID = UUID.randomUUID(),
        email: String = "user@example.com",
        authorities: List<String> = listOf("leads.view", "leads.edit"),
    ) = UserPrincipal(
        id = id,
        tenantId = tenantId,
        email = email,
        authorities = authorities.map { SimpleGrantedAuthority(it) },
    )

    // ── Access token generation ───────────────────────────────────────────────

    @Test
    fun `generateAccessToken returns a non-blank token string`() {
        val token = provider().generateAccessToken(principal())
        assertTrue(token.isNotBlank())
    }

    @Test
    fun `generateAccessToken embeds correct claims`() {
        val p = provider()
        val userId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()
        val email = "alice@example.com"
        val principal = principal(id = userId, tenantId = tenantId, email = email)

        val token = p.generateAccessToken(principal)
        val claims = requireNotNull(p.parseClaimsOrNull(token))

        assertEquals(userId.toString(), claims.subject)
        assertEquals(email, p.getEmail(claims))
        assertEquals(tenantId, p.getTenantId(claims))
        assertTrue(p.getAuthorities(claims).contains("leads.view"))
        assertTrue(p.isAccessToken(claims))
        assertFalse(p.isRefreshToken(claims))
    }

    @Test
    fun `getUserIdFromToken round-trips the principal id`() {
        val p = provider()
        val id = UUID.randomUUID()
        val token = p.generateAccessToken(principal(id = id))
        assertEquals(id, p.getUserIdFromToken(token))
    }

    @Test
    fun `getTenantIdFromToken round-trips the tenant id`() {
        val p = provider()
        val tenantId = UUID.randomUUID()
        val token = p.generateAccessToken(principal(tenantId = tenantId))
        assertEquals(tenantId, p.getTenantIdFromToken(token))
    }

    @Test
    fun `getEmailFromToken round-trips the email`() {
        val p = provider()
        val email = "bob@example.com"
        val token = p.generateAccessToken(principal(email = email))
        assertEquals(email, p.getEmailFromToken(token))
    }

    @Test
    fun `getAuthoritiesFromToken returns the embedded authority list`() {
        val p = provider()
        val auths = listOf("leads.view", "contacts.edit", "reports.view")
        val token = p.generateAccessToken(principal(authorities = auths))
        val extracted = p.getAuthoritiesFromToken(token)
        assertEquals(auths.toSet(), extracted.toSet())
    }

    // ── Refresh token generation ──────────────────────────────────────────────

    @Test
    fun `generateRefreshToken is flagged as refresh type`() {
        val p = provider()
        val token = p.generateRefreshToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        assertTrue(p.isRefreshToken(token))
        assertFalse(p.isAccessToken(token))
    }

    @Test
    fun `generateRefreshToken embeds session and family ids`() {
        val p = provider()
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val familyId = UUID.randomUUID()
        val token = p.generateRefreshToken(userId, sessionId, familyId)
        val claims = requireNotNull(p.parseClaimsOrNull(token))

        assertEquals(userId.toString(), claims.subject)
        assertEquals(sessionId, p.getRefreshSessionId(claims))
        assertEquals(familyId, p.getRefreshFamilyId(claims))
    }

    @Test
    fun `getRefreshSessionIdFromToken round-trips the session id`() {
        val p = provider()
        val sid = UUID.randomUUID()
        val token = p.generateRefreshToken(UUID.randomUUID(), sid, UUID.randomUUID())
        assertEquals(sid, p.getRefreshSessionIdFromToken(token))
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `validateToken returns true for a freshly generated token`() {
        val p = provider()
        val token = p.generateAccessToken(principal())
        assertTrue(p.validateToken(token))
    }

    @Test
    fun `validateToken returns false for a tampered token`() {
        val p = provider()
        val token = p.generateAccessToken(principal())
        val tampered = token.dropLast(5) + "XXXXX"
        assertFalse(p.validateToken(tampered))
    }

    @Test
    fun `validateToken returns false for a completely invalid string`() {
        val p = provider()
        assertFalse(p.validateToken("not.a.jwt"))
    }

    @Test
    fun `parseClaimsOrNull returns null for invalid token (no throw)`() {
        val p = provider()
        val result = p.parseClaimsOrNull("garbage")
        assertNull(result)
    }

    @Test
    fun `token signed with a different key fails validation`() {
        val otherSecret = "zyxwvutsrqponmlkjihgfedcba987654zyxwvutsrqponmlkjihgfedcba987654"
        val p1 = provider()
        val p2 = JwtTokenProvider(otherSecret, accessExpiry, refreshExpiry)

        val token = p1.generateAccessToken(principal())
        assertFalse(p2.validateToken(token))
    }

    // ── Expiry ────────────────────────────────────────────────────────────────

    @Test
    fun `expired token fails validation`() {
        // Token with 1ms expiry
        val p = JwtTokenProvider(secret, 1L, refreshExpiry)
        val token = p.generateAccessToken(principal())
        Thread.sleep(5) // ensure expiry passes
        assertFalse(p.validateToken(token))
    }

    @Test
    fun `getExpirationFromToken is after now for a fresh token`() {
        val p = provider()
        val token = p.generateAccessToken(principal())
        val expiry = p.getExpirationFromToken(token)
        assertTrue(expiry.after(java.util.Date()), "Expiry should be in the future")
    }
}
