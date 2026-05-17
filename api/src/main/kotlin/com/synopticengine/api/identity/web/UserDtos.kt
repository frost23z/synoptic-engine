package com.synopticengine.api.identity.web

import com.synopticengine.api.identity.domain.ViewPermission
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateUserRequest(
    @field:Email(message = "Valid email required")
    @field:NotBlank(message = "Email is required")
    val email: String,
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    val phone: String? = null,
    val roles: Set<String> = setOf("SALESPERSON"),
    val groups: Set<UUID> = emptySet(),
    val viewPermission: ViewPermission = ViewPermission.GLOBAL,
)

data class UpdateUserRequest(
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    val phone: String? = null,
    val roles: Set<String>? = null,
    val groups: Set<UUID>? = null,
    val viewPermission: ViewPermission? = null,
)

data class MassDeactivateRequest(
    val ids: List<UUID>,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class UserDetailResponse(
    val id: UUID,
    val email: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val phone: String?,
    val isActive: Boolean,
    val viewPermission: String,
    val roles: List<String>,
    val groups: List<Map<String, String>>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
