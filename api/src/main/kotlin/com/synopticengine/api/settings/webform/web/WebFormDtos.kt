package com.synopticengine.api.settings.webform.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import java.time.Instant
import java.util.UUID

data class WebFormFieldRequest(
    @field:NotNull(message = "Attribute ID is required")
    val attributeId: UUID,
    val sortOrder: Int = 0,
    val isRequired: Boolean = false,
)

data class CreateWebFormRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    @field:Size(max = 2_000, message = "Description must not exceed 2 000 characters")
    val description: String? = null,
    val isActive: Boolean = true,
    val createLead: Boolean = false,
    @field:Size(max = 50, message = "Background color must not exceed 50 characters")
    val backgroundColor: String? = null,
    val submitSuccessAction: String = "message",
    @field:Size(max = 2_000, message = "Success message must not exceed 2 000 characters")
    val submitSuccessMessage: String? = null,
    @field:URL(message = "Submit success URL must be a valid URL")
    val submitSuccessUrl: String? = null,
    val captchaEnabled: Boolean = false,
    @field:Size(max = 100, message = "Cannot define more than 100 form fields")
    val fields: List<WebFormFieldRequest> = emptyList(),
)

data class UpdateWebFormRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    @field:Size(max = 2_000, message = "Description must not exceed 2 000 characters")
    val description: String? = null,
    val isActive: Boolean = true,
    val createLead: Boolean = false,
    @field:Size(max = 50, message = "Background color must not exceed 50 characters")
    val backgroundColor: String? = null,
    val submitSuccessAction: String = "message",
    @field:Size(max = 2_000, message = "Success message must not exceed 2 000 characters")
    val submitSuccessMessage: String? = null,
    @field:URL(message = "Submit success URL must be a valid URL")
    val submitSuccessUrl: String? = null,
    val captchaEnabled: Boolean = false,
    @field:Size(max = 100, message = "Cannot define more than 100 form fields")
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
