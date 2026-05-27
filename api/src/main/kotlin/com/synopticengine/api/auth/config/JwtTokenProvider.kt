package com.synopticengine.api.auth.config

import com.synopticengine.api.auth.UserPrincipal
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * T6.8 — JWT provider refactors:
 * 1. `.claims(map)` is called **first** in the builder chain so downstream
 *    `.subject()`, `.issuedAt()`, and `.expiration()` calls set standard
 *    claims that cannot be overwritten by the map merge.
 * 2. Callers that have already parsed a token (e.g., [JwtAuthFilter]) pass
 *    the [Claims] object directly to the `*FromClaims` methods, avoiding
 *    repeat parsing.
 * 3. [parseClaimsOrNull] is the single parse entry-point; it returns null
 *    on any validation failure instead of throwing.
 */
@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-expiry}") private val accessTokenExpiry: Long,
    @Value("\${jwt.refresh-token-expiry}") private val refreshTokenExpiry: Long,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateAccessToken(principal: UserPrincipal): String =
        buildToken(
            subject = principal.id.toString(),
            expiry = accessTokenExpiry,
            extra =
                mapOf(
                    "email" to principal.email,
                    "tenantId" to principal.tenantId.toString(),
                    "authorities" to principal.authorities.map { it.authority },
                    "type" to "access",
                ),
        )

    fun generateRefreshToken(
        userId: UUID,
        sessionId: UUID,
        familyId: UUID,
    ): String =
        buildToken(
            subject = userId.toString(),
            expiry = refreshTokenExpiry,
            extra =
                mapOf(
                    "type" to "refresh",
                    "sid" to sessionId.toString(),
                    "fid" to familyId.toString(),
                ),
        )

    /** Parse and validate the token, returning null on any failure. */
    fun parseClaimsOrNull(token: String): Claims? = runCatching { parseClaims(token) }.getOrNull()

    /** True if the token parses and validates (kept for callers that don't need claims). */
    fun validateToken(token: String): Boolean = parseClaimsOrNull(token) != null

    // ── Claims-based accessors (use when claims are already parsed) ───────────

    fun getUserId(claims: Claims): UUID = UUID.fromString(claims.subject)

    fun getEmail(claims: Claims): String = claims["email"] as String

    fun getTenantId(claims: Claims): UUID = UUID.fromString(claims["tenantId"] as String)

    @Suppress("UNCHECKED_CAST")
    fun getAuthorities(claims: Claims): List<String> = claims["authorities"] as List<String>

    fun getRefreshSessionId(claims: Claims): UUID = UUID.fromString(claims["sid"] as String)

    fun getRefreshFamilyId(claims: Claims): UUID = UUID.fromString(claims["fid"] as String)

    fun isRefreshToken(claims: Claims): Boolean = claims["type"] == "refresh"

    fun isAccessToken(claims: Claims): Boolean = claims["type"] == "access"

    // ── Token-string overloads (kept for callers that haven't parsed yet) ─────

    fun getUserIdFromToken(token: String): UUID = getUserId(parseClaims(token))

    fun getEmailFromToken(token: String): String = getEmail(parseClaims(token))

    fun getTenantIdFromToken(token: String): UUID = getTenantId(parseClaims(token))

    @Suppress("UNCHECKED_CAST")
    fun getAuthoritiesFromToken(token: String): List<String> = getAuthorities(parseClaims(token))

    fun getRefreshSessionIdFromToken(token: String): UUID = getRefreshSessionId(parseClaims(token))

    fun getRefreshFamilyIdFromToken(token: String): UUID = getRefreshFamilyId(parseClaims(token))

    fun getExpirationFromToken(token: String): Date = parseClaims(token).expiration

    fun isRefreshToken(token: String): Boolean = isRefreshToken(parseClaims(token))

    fun isAccessToken(token: String): Boolean = isAccessToken(parseClaims(token))

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun buildToken(
        subject: String,
        expiry: Long,
        extra: Map<String, Any>,
    ): String {
        val now = Date()
        // T6.8: set extra claims first so standard claims (sub, iat, exp) are
        // not silently overwritten by a map merge containing those keys.
        return Jwts
            .builder()
            .claims(extra)
            .subject(subject)
            .issuedAt(now)
            .expiration(Date(now.time + expiry))
            .signWith(key)
            .compact()
    }

    private fun parseClaims(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}
