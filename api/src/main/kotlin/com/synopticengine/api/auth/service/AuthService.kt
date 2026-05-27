package com.synopticengine.api.auth.service

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.auth.config.JwtTokenProvider
import com.synopticengine.api.auth.domain.PasswordReset
import com.synopticengine.api.auth.domain.RefreshSession
import com.synopticengine.api.auth.repo.PasswordResetRepository
import com.synopticengine.api.auth.repo.RefreshSessionRepository
import com.synopticengine.api.auth.web.TokenResponse
import com.synopticengine.api.identity.IdentityApi
import com.synopticengine.api.identity.UserCredentials
import com.synopticengine.api.shared.email.MailSenderService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class AuthService(
    private val identityApi: IdentityApi,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val passwordResetRepository: PasswordResetRepository,
    private val refreshSessionRepository: RefreshSessionRepository,
    private val mailSenderService: MailSenderService,
    private val loginAttemptTracker: LoginAttemptTracker,
    private val forgotPasswordAttemptTracker: ForgotPasswordAttemptTracker,
    @Value("\${synoptic.auth.password-reset.ttl-minutes:15}") private val resetTtlMinutes: Long,
) {
    fun login(
        email: String,
        password: String,
        clientIp: String? = null,
    ): TokenResponse {
        // H6 — lockout check BEFORE we even hit the credential lookup. Stops
        // brute-force from being limited only by SMTP / DB throughput.
        loginAttemptTracker.assertNotLocked(email, clientIp)

        val credentials = identityApi.findCredentialsByEmail(email)
        if (credentials == null) {
            // Timing-oracle equalization: perform a dummy bcrypt match so the
            // response time for an unknown email is similar to that for a known
            // email with a wrong password. Without this, timing differences let
            // attackers enumerate valid email addresses.
            passwordEncoder.matches(password, DUMMY_BCRYPT_HASH)
            loginAttemptTracker.recordFailure(email, clientIp)
            throw IllegalArgumentException("Invalid email or password")
        }

        if (!passwordEncoder.matches(password, credentials.passwordHash)) {
            loginAttemptTracker.recordFailure(email, clientIp)
            throw IllegalArgumentException("Invalid email or password")
        }

        credentials.requireActive()
        loginAttemptTracker.recordSuccess(email, clientIp)

        return buildTokenResponse(credentials)
    }

    @Transactional(noRollbackFor = [IllegalArgumentException::class])
    fun refresh(refreshToken: String): TokenResponse {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("Invalid refresh token")
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw IllegalArgumentException("Token is not a refresh token")
        }

        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)
        val sessionId = jwtTokenProvider.getRefreshSessionIdFromToken(refreshToken)
        val familyId = jwtTokenProvider.getRefreshFamilyIdFromToken(refreshToken)
        val now = Instant.now()
        val session =
            refreshSessionRepository.findById(sessionId).orElse(null)
                ?: run {
                    refreshSessionRepository.revokeFamily(familyId, now, "INVALID_OR_REUSED_TOKEN")
                    throw IllegalArgumentException("Invalid refresh token")
                }
        if (session.userId != userId || session.familyId != familyId || session.tokenHash != hashToken(refreshToken)) {
            refreshSessionRepository.revokeFamily(familyId, now, "INVALID_OR_REUSED_TOKEN")
            throw IllegalArgumentException("Invalid refresh token")
        }
        if (session.revokedAt != null) {
            refreshSessionRepository.revokeFamily(familyId, now, "TOKEN_REUSE_DETECTED")
            throw IllegalArgumentException("Refresh token has been revoked")
        }
        if (session.isExpired(now)) {
            refreshSessionRepository.revokeFamily(familyId, now, "TOKEN_EXPIRED")
            throw IllegalArgumentException("Refresh token has expired")
        }

        val credentials = identityApi.findCredentialsById(userId) ?: throw IllegalArgumentException("User not found")

        credentials.requireActive()

        val (newRefreshToken, newSessionId) =
            issueRefreshToken(
                userId = credentials.id,
                familyId = familyId,
                parentSessionId = session.id,
            )
        session.revokedAt = now
        session.revokedReason = "ROTATED"
        session.replacedBySessionId = newSessionId
        refreshSessionRepository.save(session)

        return TokenResponse(
            accessToken = jwtTokenProvider.generateAccessToken(credentials.toPrincipal()),
            refreshToken = newRefreshToken,
            userId = credentials.id,
            email = credentials.email,
            fullName = credentials.fullName,
            authorities = credentials.authorities,
        )
    }

    private fun buildTokenResponse(credentials: UserCredentials): TokenResponse {
        val principal = credentials.toPrincipal()
        val (refreshToken, _) = issueRefreshToken(credentials.id, UUID.randomUUID(), null)
        return TokenResponse(
            accessToken = jwtTokenProvider.generateAccessToken(principal),
            refreshToken = refreshToken,
            userId = credentials.id,
            email = credentials.email,
            fullName = credentials.fullName,
            authorities = credentials.authorities,
        )
    }

    private fun UserCredentials.toPrincipal() =
        UserPrincipal(
            id = id,
            tenantId = tenantId,
            email = email,
            authorities = authorities.map { SimpleGrantedAuthority(it) }.toSet(),
        )

    private fun UserCredentials.requireActive() {
        if (!isActive || deletedAt != null) {
            throw IllegalStateException("Account is deactivated")
        }
    }

    @Transactional
    fun forgotPassword(
        email: String,
        clientIp: String? = null,
    ) {
        forgotPasswordAttemptTracker.assertNotLocked(email, clientIp)
        forgotPasswordAttemptTracker.recordFailure(email, clientIp)
        identityApi.findCredentialsByEmail(email) ?: return // silent: don't reveal if email exists
        // H7 — token sent to user is high-entropy random; stored hash is BCrypt.
        // A read-only leak of `user_password_resets` (e.g. backup snapshot)
        // therefore cannot be replayed to take over the account.
        val plaintextToken = generateResetToken()
        val tokenHash =
            passwordEncoder.encode(plaintextToken)
                ?: throw IllegalStateException("Token encoding failed")
        passwordResetRepository.save(
            PasswordReset().apply {
                this.email = email
                this.token = tokenHash
            },
        )
        // Send inside the transaction so SMTP failures roll back the unused token row —
        // we'd rather the user retry than leave a stranded token they never received.
        mailSenderService.sendEmail(
            to = email,
            subject = "Password Reset",
            body = "Your password reset token: $plaintextToken\n\nThis token expires in $resetTtlMinutes minutes.",
        )
    }

    @Transactional
    fun resetPassword(
        token: String,
        email: String,
        newPassword: String,
    ) {
        // H7 — the stored row is keyed by email (one outstanding reset per
        // email at a time). Compare the supplied plaintext against the BCrypt
        // hash; constant-time inside passwordEncoder.matches.
        val reset =
            passwordResetRepository.findById(email).orElse(null)
                ?: throw IllegalArgumentException("Invalid or expired token")
        if (!passwordEncoder.matches(token, reset.token)) {
            throw IllegalArgumentException("Invalid or expired token")
        }
        if (reset.isExpired(resetTtlMinutes)) {
            throw IllegalArgumentException("Token has expired. Please request a new password reset.")
        }
        val encodedPassword =
            passwordEncoder.encode(newPassword)
                ?: throw IllegalStateException("Password encoding failed")
        identityApi.updatePassword(email, encodedPassword)
        // Revoke all existing sessions so the old-password holder can no longer use any refresh tokens.
        val user = identityApi.findCredentialsByEmail(email)
        if (user != null) {
            refreshSessionRepository.revokeAllByUserId(user.id, Instant.now(), "PASSWORD_RESET")
        }
        passwordResetRepository.deleteByEmail(email)
    }

    @Transactional
    fun logout(
        userId: UUID,
        refreshToken: String,
    ) {
        if (!jwtTokenProvider.validateToken(refreshToken)) return // already invalid
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) return
        val sessionId =
            runCatching { jwtTokenProvider.getRefreshSessionIdFromToken(refreshToken) }.getOrNull() ?: return
        val session = refreshSessionRepository.findById(sessionId).orElse(null) ?: return
        if (session.userId != userId || session.revokedAt != null) return
        session.revokedAt = Instant.now()
        session.revokedReason = "LOGOUT"
        refreshSessionRepository.save(session)
    }

    @Transactional
    fun logoutAll(userId: UUID) {
        refreshSessionRepository.revokeAllByUserId(userId, Instant.now(), "LOGOUT_ALL")
    }

    private fun generateResetToken(): String {
        // 32 bytes = 256 bits of entropy; URL-safe Base64 so it survives copy-paste.
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val secureRandom = SecureRandom()

        // A pre-computed BCrypt hash of a random string. Used to perform a
        // constant-time dummy password check when the supplied email does not
        // exist, so that timing differences cannot reveal whether an email is
        // registered (email-enumeration timing oracle, T1.4).
        // Re-generate with: BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(10))
        private const val DUMMY_BCRYPT_HASH = "\$2a\$10\$X9ztv7bG1Ym0Uf8U3PqrOuZ0W2XL0NxoZKx6K8oX/OtR5oL6BUHK."
    }

    private fun issueRefreshToken(
        userId: UUID,
        familyId: UUID,
        parentSessionId: UUID?,
    ): Pair<String, UUID> {
        val sessionId = UUID.randomUUID()
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId, sessionId, familyId)
        refreshSessionRepository.save(
            RefreshSession().apply {
                this.id = sessionId
                this.userId = userId
                this.familyId = familyId
                this.parentSessionId = parentSessionId
                this.tokenHash = hashToken(refreshToken)
                this.issuedAt = Instant.now()
                this.expiresAt = jwtTokenProvider.getExpirationFromToken(refreshToken).toInstant()
            },
        )
        return refreshToken to sessionId
    }

    private fun hashToken(token: String): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
