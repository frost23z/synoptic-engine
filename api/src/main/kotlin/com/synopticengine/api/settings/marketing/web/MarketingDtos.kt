package com.synopticengine.api.settings.marketing.web

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

// ── Marketing Event DTOs ──────────────────────────────────────────────────────

data class MarketingEventResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class CreateMarketingEventRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
)

data class UpdateMarketingEventRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
)

data class MassDestroyRequest(
    val ids: List<UUID>,
)

// ── Marketing Campaign DTOs ───────────────────────────────────────────────────

data class MarketingCampaignResponse(
    val id: UUID,
    val name: String,
    val subject: String,
    val description: String?,
    val eventId: UUID?,
    val emailTemplateId: UUID?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class CreateMarketingCampaignRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val subject: String,
    val description: String? = null,
    val eventId: UUID? = null,
    val emailTemplateId: UUID? = null,
)

data class UpdateMarketingCampaignRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val subject: String,
    val description: String? = null,
    val eventId: UUID? = null,
    val emailTemplateId: UUID? = null,
)

data class ExecuteMarketingCampaignRequest(
    val recipients: List<String>,
    val context: Map<String, String> = emptyMap(),
)

data class ExecuteMarketingCampaignResponse(
    val campaignId: UUID,
    val requested: Int,
    val sent: Int,
)
