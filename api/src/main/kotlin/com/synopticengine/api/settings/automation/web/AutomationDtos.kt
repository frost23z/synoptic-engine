package com.synopticengine.api.settings.automation.web

import jakarta.validation.constraints.NotBlank
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
    val events: List<String>,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class CreateWebhookRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val payloadUrl: String,
    val events: List<String> = emptyList(),
    val isActive: Boolean = true,
)

data class UpdateWebhookRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val payloadUrl: String,
    val events: List<String> = emptyList(),
    val isActive: Boolean = true,
)
