package com.synopticengine.api.settings.marketing.web

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ── Marketing Event DTOs ──────────────────────────────────────────────────────

data class MarketingEventResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val eventDate: LocalDate?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class CreateMarketingEventRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val eventDate: LocalDate? = null,
)

data class UpdateMarketingEventRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val eventDate: LocalDate? = null,
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
    /**
     * Recipient email addresses.
     *
     * T2.5(c) constraints:
     *  - At most [MAX_RECIPIENTS] entries per request to prevent runaway fan-out.
     *  - Each entry validated as a syntactically well-formed RFC 5321 email address.
     */
    @field:Size(max = MAX_RECIPIENTS, message = "Recipient list must not exceed $MAX_RECIPIENTS addresses")
    val recipients: List<
        @Email(message = "Each recipient must be a valid email address")
        String,
    >,
    val context: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Maximum number of recipients per execute-campaign call. T2.5(c). */
        const val MAX_RECIPIENTS = 1_000
    }
}

data class ExecuteMarketingCampaignResponse(
    val campaignId: UUID,
    val requested: Int,
    val sent: Int,
)
