package com.synopticengine.api.auth.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LoginRequest(
    @field:Email(message = "Valid email required")
    @field:NotBlank(message = "Email is required")
    val email: String,
    @field:NotBlank(message = "Password is required")
    @field:Size(max = 1000, message = "Password must not exceed 1000 characters")
    val password: String,
)

data class RefreshRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String,
)

data class RegisterRequest(
    @field:NotBlank(message = "Company name is required")
    @field:Size(max = 255, message = "Company name must not exceed 255 characters")
    val companyName: String,
    @field:Email(message = "Valid email required")
    @field:NotBlank(message = "Email is required")
    val email: String,
    @field:Size(min = 8, max = 1000, message = "Password must be 8–1000 characters")
    val password: String,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val userId: UUID,
    val tenantId: UUID,
    val email: String,
    val fullName: String,
    val authorities: List<String>,
    val mfaRequired: Boolean = false,
    val mfaToken: String? = null,
)

data class MeResponse(
    val id: UUID,
    val tenantId: UUID,
    val email: String,
    val fullName: String,
    val isActive: Boolean,
    val authorities: List<String>,
)

data class ForgotPasswordRequest(
    @field:Email(message = "Valid email required")
    @field:NotBlank(message = "Email is required")
    val email: String,
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Token is required")
    val token: String,
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Valid email required")
    val email: String,
    @field:Size(min = 8, max = 1000, message = "Password must be 8–1000 characters")
    val newPassword: String,
)

data class UpdateMeRequest(
    @field:NotBlank(message = "First name is required")
    @field:Size(max = 255, message = "First name must not exceed 255 characters")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    @field:Size(max = 255, message = "Last name must not exceed 255 characters")
    val lastName: String,
    @field:Size(max = 50, message = "Phone must not exceed 50 characters")
    val phone: String? = null,
    val currentPassword: String? = null,
    @field:Size(min = 8, max = 1000, message = "New password must be 8–1000 characters")
    val newPassword: String? = null,
)

data class SessionResponse(
    val id: UUID,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

data class LoginHistoryResponse(
    val id: UUID,
    val clientIp: String?,
    val loggedInAt: Instant,
)

data class CreateApiKeyRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 200, message = "Name must not exceed 200 characters")
    val name: String,
    val expiresAt: Instant? = null,
)

data class ApiKeyResponse(
    val id: UUID,
    val name: String,
    val prefix: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
)

data class ApiKeyCreateResponse(
    val id: UUID,
    val name: String,
    val key: String,
    val prefix: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
)
