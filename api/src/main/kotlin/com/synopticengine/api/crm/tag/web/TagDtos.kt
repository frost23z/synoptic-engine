package com.synopticengine.api.crm.tag.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateTagRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val color: String? = null,
)

data class UpdateTagRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val color: String? = null,
)

data class TagResponse(
    val id: UUID,
    val name: String,
    val color: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class MassDestroyTagRequest(
    val ids: List<UUID>,
)
