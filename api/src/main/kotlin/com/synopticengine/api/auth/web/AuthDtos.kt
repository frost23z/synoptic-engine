package com.synopticengine.api.auth.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class LoginRequest(
    @field:Email(message = "Valid email required")
    @field:NotBlank(message = "Email is required")
    val email: String,
    @field:NotBlank(message = "Password is required")
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
    @field:jakarta.validation.constraints.Email val email: String,
)

data class ResetPasswordRequest(
    val token: String,
    val email: String,
    @field:jakarta.validation.constraints.Size(min = 8, message = "Password must be at least 8 characters")
    val newPassword: String,
)
