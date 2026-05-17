package com.synopticengine.api.auth.service

import com.synopticengine.api.auth.config.JwtTokenProvider
import com.synopticengine.api.auth.config.UserPrincipal
import com.synopticengine.api.auth.domain.PasswordReset
import com.synopticengine.api.auth.repo.PasswordResetRepository
import com.synopticengine.api.auth.web.TokenResponse
import com.synopticengine.api.identity.IdentityApi
import com.synopticengine.api.identity.UserCredentials
import com.synopticengine.api.shared.email.MailSenderService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val identityApi: IdentityApi,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val passwordResetRepository: PasswordResetRepository,
    private val mailSenderService: MailSenderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun login(
        email: String,
        password: String,
    ): TokenResponse {
        val credentials =
            identityApi.findCredentialsByEmail(email)
                ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(password, credentials.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        credentials.requireActive()

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
        val token =
            java.util.UUID
                .randomUUID()
                .toString()
        passwordResetRepository.save(
            PasswordReset().apply {
                this.email = email
                this.token = token
            },
        )
        try {
            mailSenderService.sendEmail(
                to = email,
                subject = "Password Reset",
                body = "Your password reset token: $token\n\nThis token expires in 60 minutes.",
            )
        } catch (e: Exception) {
            log.warn("Failed to send password reset email", e)
        }
    }

    @Transactional
    fun resetPassword(
        token: String,
        email: String,
        newPassword: String,
    ) {
        val reset =
            passwordResetRepository.findByToken(token)
                ?: throw IllegalArgumentException("Invalid or expired token")
        if (reset.email != email) throw IllegalArgumentException("Invalid or expired token")
        if (reset.isExpired()) throw IllegalArgumentException("Token has expired. Please request a new password reset.")
        val encodedPassword =
            passwordEncoder.encode(newPassword)
                ?: throw IllegalStateException("Password encoding failed")
        identityApi.updatePassword(email, encodedPassword)
        passwordResetRepository.deleteByEmail(email)
    }
}
