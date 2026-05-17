package com.synopticengine.api.crm.lead.web

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

// ── Stage ─────────────────────────────────────────────────────────────────────

data class CreateStageRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val sortOrder: Int = 0,
    val color: String? = null,
    @field:Min(0) @field:Max(100)
    val probability: Int = 0,
    val code: String? = null,
)

data class UpdateStageRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val sortOrder: Int = 0,
    val color: String? = null,
    @field:Min(0) @field:Max(100)
    val probability: Int = 0,
    val code: String? = null,
)

data class StageResponse(
    val id: UUID,
    val pipelineId: UUID,
    val name: String,
    val sortOrder: Int,
    val color: String?,
    val probability: Int,
    val code: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

// ── Pipeline ──────────────────────────────────────────────────────────────────

data class CreatePipelineRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    val isActive: Boolean = true,
    val rottenDays: Int = 30,
)

data class UpdatePipelineRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val rottenDays: Int = 30,
)

data class PipelineResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val isActive: Boolean,
    val isDefault: Boolean,
    val rottenDays: Int,
    val stages: List<StageResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

// ── Lead Sources / Types ──────────────────────────────────────────────────────

data class CreateLeadSourceRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
)

data class CreateLeadTypeRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
)

data class LeadSourceResponse(
    val id: UUID,
    val name: String,
    val createdAt: Instant?,
)

data class LeadTypeResponse(
    val id: UUID,
    val name: String,
    val createdAt: Instant?,
)

data class StageOrderEntry(
    val id: UUID,
    val sortOrder: Int,
)

data class ReorderStagesRequest(
    val order: List<StageOrderEntry>,
)
