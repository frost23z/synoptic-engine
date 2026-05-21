package com.synopticengine.api.settings.emailtemplate.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateEmailTemplateRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Subject is required")
    val subject: String,
    @field:NotBlank(message = "Content is required")
    val content: String,
)

data class UpdateEmailTemplateRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Subject is required")
    val subject: String,
    @field:NotBlank(message = "Content is required")
    val content: String,
)

data class EmailTemplateResponse(
    val id: UUID,
    val name: String,
    val subject: String,
    val content: String,
    val isPredefined: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class RenderEmailTemplateRequest(
    val context: Map<String, String> = emptyMap(),
)
