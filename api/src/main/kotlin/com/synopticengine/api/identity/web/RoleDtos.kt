package com.synopticengine.api.identity.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateRoleRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    val permissions: Set<String> = emptySet(),
)

data class UpdateRoleRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    val permissions: Set<String> = emptySet(),
)

data class RoleResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val permissions: List<String>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class PermissionResponse(
    val id: UUID,
    val name: String,
    val description: String?,
)
