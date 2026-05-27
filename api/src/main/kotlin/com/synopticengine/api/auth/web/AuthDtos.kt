package com.synopticengine.api.auth.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val userId: UUID,
    val email: String,
    val fullName: String,
    val authorities: List<String>,
)

data class MeResponse(
    val id: UUID,
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
