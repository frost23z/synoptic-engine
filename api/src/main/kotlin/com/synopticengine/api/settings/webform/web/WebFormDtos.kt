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
    val createLead: Boolean = false,
    val backgroundColor: String? = null,
    val submitSuccessAction: String = "message",
    val submitSuccessMessage: String? = null,
    val submitSuccessUrl: String? = null,
    val captchaEnabled: Boolean = false,
    val fields: List<WebFormFieldRequest> = emptyList(),
)

data class UpdateWebFormRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    val isActive: Boolean = true,
    val createLead: Boolean = false,
    val backgroundColor: String? = null,
    val submitSuccessAction: String = "message",
    val submitSuccessMessage: String? = null,
    val submitSuccessUrl: String? = null,
    val captchaEnabled: Boolean = false,
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
    val createLead: Boolean,
    val backgroundColor: String?,
    val submitSuccessAction: String,
    val submitSuccessMessage: String?,
    val submitSuccessUrl: String?,
    val captchaEnabled: Boolean,
    val fields: List<WebFormFieldResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class WebFormSubmitRequest(
    val values: Map<String, String>,
    val captchaToken: String? = null,
)

data class WebFormSubmitResponse(
    val success: Boolean,
    val message: String,
    val personId: UUID? = null,
    val leadId: UUID? = null,
)
