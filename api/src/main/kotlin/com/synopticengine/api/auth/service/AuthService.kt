package com.synopticengine.api.auth.service

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.auth.config.JwtTokenProvider
import com.synopticengine.api.auth.domain.PasswordReset
import com.synopticengine.api.auth.repo.PasswordResetRepository
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
import java.util.Base64

@Service
class AuthService(
    private val identityApi: IdentityApi,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val passwordResetRepository: PasswordResetRepository,
    private val mailSenderService: MailSenderService,
    private val loginAttemptTracker: LoginAttemptTracker,
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

    fun refresh(refreshToken: String): TokenResponse {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("Invalid refresh token")
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw IllegalArgumentException("Token is not a refresh token")
        }

        val credentials =
            identityApi.findCredentialsById(
                jwtTokenProvider.getUserIdFromToken(refreshToken),
            ) ?: throw IllegalArgumentException("User not found")

        credentials.requireActive()

        return buildTokenResponse(credentials)
    }

    private fun buildTokenResponse(credentials: UserCredentials): TokenResponse {
        val principal = credentials.toPrincipal()
        return TokenResponse(
            accessToken = jwtTokenProvider.generateAccessToken(principal),
            refreshToken = jwtTokenProvider.generateRefreshToken(credentials.id),
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
    fun forgotPassword(email: String) {
        identityApi.findCredentialsByEmail(email) ?: return // silent: don't reveal if email exists
        // H7 — token sent to user is high-entropy random; stored hash is BCrypt.
        // A read-only leak of `user_password_resets` (e.g. backup snapshot)
        // therefore cannot be replayed to take over the account.
        val plaintextToken = generateResetToken()
        val tokenHash = passwordEncoder.encode(plaintextToken)
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
        passwordResetRepository.deleteByEmail(email)
    }

    private fun generateResetToken(): String {
        // 32 bytes = 256 bits of entropy; URL-safe Base64 so it survives copy-paste.
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
