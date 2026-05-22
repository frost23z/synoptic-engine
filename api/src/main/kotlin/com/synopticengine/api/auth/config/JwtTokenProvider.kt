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
            claims =
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
            claims =
                mapOf(
                    "type" to "refresh",
                    "sid" to sessionId.toString(),
                    "fid" to familyId.toString(),
                ),
        )

    fun validateToken(token: String): Boolean = runCatching { parseClaims(token) }.isSuccess

    fun getUserIdFromToken(token: String): UUID = UUID.fromString(parseClaims(token).subject)

    fun getEmailFromToken(token: String): String = parseClaims(token)["email"] as String

    fun getTenantIdFromToken(token: String): UUID = UUID.fromString(parseClaims(token)["tenantId"] as String)

    @Suppress("UNCHECKED_CAST")
    fun getAuthoritiesFromToken(token: String): List<String> = parseClaims(token)["authorities"] as List<String>

    fun getRefreshSessionIdFromToken(token: String): UUID = UUID.fromString(parseClaims(token)["sid"] as String)

    fun getRefreshFamilyIdFromToken(token: String): UUID = UUID.fromString(parseClaims(token)["fid"] as String)

    fun getExpirationFromToken(token: String): Date = parseClaims(token).expiration

    fun isRefreshToken(token: String): Boolean = parseClaims(token)["type"] == "refresh"

    fun isAccessToken(token: String): Boolean = parseClaims(token)["type"] == "access"

    private fun buildToken(
        subject: String,
        expiry: Long,
        claims: Map<String, Any>,
    ): String {
        val now = Date()
        return Jwts
            .builder()
            .subject(subject)
            .issuedAt(now)
            .expiration(Date(now.time + expiry))
            .claims(claims)
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
