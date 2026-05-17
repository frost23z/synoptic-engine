package com.synopticengine.api.identity.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateGroupRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
)

data class UpdateGroupRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
)

data class GroupResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
