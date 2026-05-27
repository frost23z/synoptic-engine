package com.synopticengine.api.settings.automation.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import java.time.Instant
import java.util.UUID

// ── Workflow DTOs ─────────────────────────────────────────────────────────────

data class WorkflowResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val eventName: String,
    val conditions: List<Map<String, Any?>>,
    val actions: List<Map<String, Any?>>,
    val conditionType: String,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class CreateWorkflowRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    @field:NotBlank val eventName: String,
    val conditions: List<Map<String, Any?>> = emptyList(),
    val actions: List<Map<String, Any?>> = emptyList(),
    /** `and` (default) or `or`. */
    val conditionType: String = "and",
    val isActive: Boolean = true,
)

data class UpdateWorkflowRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    @field:NotBlank val eventName: String,
    val conditions: List<Map<String, Any?>> = emptyList(),
    val actions: List<Map<String, Any?>> = emptyList(),
    val conditionType: String = "and",
    val isActive: Boolean = true,
)

data class WorkflowActionRunResponse(
    val id: UUID,
    val workflowId: UUID,
    val eventName: String,
    val entityType: String,
    val entityId: UUID,
    val actionType: String,
    val status: String,
    val errorMessage: String?,
    val payload: Map<String, Any?>?,
    val createdAt: Instant?,
)

// ── Webhook DTOs ──────────────────────────────────────────────────────────────

data class WebhookResponse(
    val id: UUID,
    val name: String,
    val payloadUrl: String,
    val hasSecret: Boolean,
    val events: List<String>,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class CreateWebhookRequest(
    @field:NotBlank val name: String,
    /**
     * Outbound webhook URL. Must be a valid URL (https preferred; http allowed for local/dev).
     * The SSRF validator in [OutboundUrlValidator] also enforces non-RFC-1918 hosts at runtime.
     */
    @field:NotBlank
    @field:URL(message = "Payload URL must be a valid URL")
    val payloadUrl: String,
    @field:Size(max = 500, message = "Secret must not exceed 500 characters")
    val secret: String? = null,
    @field:Size(max = 100, message = "Cannot subscribe to more than 100 events")
    val events: List<String> = emptyList(),
    val isActive: Boolean = true,
)

data class UpdateWebhookRequest(
    @field:NotBlank val name: String,
    @field:NotBlank
    @field:URL(message = "Payload URL must be a valid URL")
    val payloadUrl: String,
    @field:Size(max = 500, message = "Secret must not exceed 500 characters")
    val secret: String? = null,
    @field:Size(max = 100, message = "Cannot subscribe to more than 100 events")
    val events: List<String> = emptyList(),
    val isActive: Boolean = true,
)

data class WebhookDeliveryRunResponse(
    val id: UUID,
    val webhookId: UUID,
    val eventName: String,
    val entityType: String,
    val entityId: UUID,
    val status: String,
    val responseCode: Int?,
    val responseBody: String?,
    val errorMessage: String?,
    val createdAt: Instant?,
)
