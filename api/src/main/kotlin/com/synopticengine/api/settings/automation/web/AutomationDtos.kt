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
    val conditions: List<Map<String, String>>,
    val actions: List<Map<String, String>>,
    val isActive: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class CreateWorkflowRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    @field:NotBlank val eventName: String,
    val conditions: List<Map<String, String>> = emptyList(),
    val actions: List<Map<String, String>> = emptyList(),
    val isActive: Boolean = true,
)

data class UpdateWorkflowRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    @field:NotBlank val eventName: String,
    val conditions: List<Map<String, String>> = emptyList(),
    val actions: List<Map<String, String>> = emptyList(),
    val isActive: Boolean = true,
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
