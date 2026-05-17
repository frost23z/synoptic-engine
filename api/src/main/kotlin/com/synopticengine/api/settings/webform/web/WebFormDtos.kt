package com.synopticengine.api.settings.webform.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class WebFormFieldRequest(
    val attributeId: UUID,
    val sortOrder: Int = 0,
    val isRequired: Boolean = false,
)

data class CreateWebFormRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    val isActive: Boolean = true,
    val fields: List<WebFormFieldRequest> = emptyList(),
)

data class UpdateWebFormRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    val isActive: Boolean = true,
    val fields: List<WebFormFieldRequest> = emptyList(),
)

data class WebFormFieldResponse(
    val id: UUID,
    val attributeId: UUID,
    val sortOrder: Int,
    val isRequired: Boolean,
)

data class WebFormResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val isActive: Boolean,
    val fields: List<WebFormFieldResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class WebFormSubmitRequest(
    val values: Map<String, String>,
)

data class WebFormSubmitResponse(
    val success: Boolean,
    val message: String,
)
