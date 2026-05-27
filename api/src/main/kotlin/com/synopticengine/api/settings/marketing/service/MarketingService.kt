package com.synopticengine.api.settings.marketing.service

import com.synopticengine.api.settings.emailtemplate.repo.EmailTemplateRepository
import com.synopticengine.api.settings.marketing.domain.MarketingCampaign
import com.synopticengine.api.settings.marketing.domain.MarketingEvent
import com.synopticengine.api.settings.marketing.domain.MarketingSendJob
import com.synopticengine.api.settings.marketing.repo.MarketingCampaignRepository
import com.synopticengine.api.settings.marketing.repo.MarketingEventRepository
import com.synopticengine.api.settings.marketing.repo.MarketingSendJobRepository
import com.synopticengine.api.settings.marketing.web.ExecuteMarketingCampaignResponse
import com.synopticengine.api.settings.marketing.web.MarketingCampaignResponse
import com.synopticengine.api.settings.marketing.web.MarketingEventResponse
import com.synopticengine.api.shared.email.HtmlSanitizer
import com.synopticengine.api.shared.email.interpolateTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MarketingService(
    private val eventRepository: MarketingEventRepository,
    private val campaignRepository: MarketingCampaignRepository,
    private val emailTemplateRepository: EmailTemplateRepository,
    private val sendJobRepository: MarketingSendJobRepository,
) {
    // ── Marketing Events ──────────────────────────────────────────────────

    fun findAllEvents(): List<MarketingEventResponse> = eventRepository.findAll().map { it.toResponse() }

    fun findEventById(id: UUID): MarketingEventResponse = requireEvent(id).toResponse()

    @Transactional
    fun createEvent(
        name: String,
        description: String?,
        eventDate: java.time.LocalDate?,
    ): MarketingEventResponse =
        eventRepository
            .save(
                MarketingEvent().apply {
                    this.name = name
                    this.description = description
                    this.eventDate = eventDate
                },
            ).toResponse()

    @Transactional
    fun updateEvent(
        id: UUID,
        name: String,
        description: String?,
        eventDate: java.time.LocalDate?,
    ): MarketingEventResponse {
        val event = requireEvent(id)
        event.name = name
        event.description = description
        event.eventDate = eventDate
        return eventRepository.save(event).toResponse()
    }

    @Transactional
    fun deleteEvent(id: UUID) {
        if (campaignRepository.existsActiveByEventId(id)) {
            throw IllegalStateException("Cannot delete marketing event with active campaigns")
        }
        eventRepository.delete(requireEvent(id))
    }

    @Transactional
    fun massDestroyEvents(ids: List<UUID>) =
        ids.forEach { id ->
            if (!campaignRepository.existsActiveByEventId(id)) {
                eventRepository.findActiveById(id)?.let { eventRepository.delete(it) }
            }
        }

    // ── Marketing Campaigns ───────────────────────────────────────────────

    fun findAllCampaigns(): List<MarketingCampaignResponse> = campaignRepository.findAll().map { it.toResponse() }

    fun findCampaignById(id: UUID): MarketingCampaignResponse = requireCampaign(id).toResponse()

    @Transactional
    fun createCampaign(
        name: String,
        subject: String,
        description: String?,
        eventId: UUID?,
        emailTemplateId: UUID?,
    ): MarketingCampaignResponse =
        campaignRepository
            .save(
                MarketingCampaign().apply {
                    this.name = name
                    this.subject = subject
                    this.description = description
                    this.eventId = eventId
                    this.emailTemplateId = emailTemplateId
                },
            ).toResponse()

    @Transactional
    fun updateCampaign(
        id: UUID,
        name: String,
        subject: String,
        description: String?,
        eventId: UUID?,
        emailTemplateId: UUID?,
    ): MarketingCampaignResponse {
        val campaign = requireCampaign(id)
        campaign.name = name
        campaign.subject = subject
        campaign.description = description
        campaign.eventId = eventId
        campaign.emailTemplateId = emailTemplateId
        return campaignRepository.save(campaign).toResponse()
    }

    @Transactional
    fun deleteCampaign(id: UUID) {
        campaignRepository.delete(requireCampaign(id))
    }

    @Transactional
    fun massDestroyCampaigns(ids: List<UUID>) =
        ids.forEach { id -> campaignRepository.findActiveById(id)?.let { campaignRepository.delete(it) } }

    @Transactional
    fun executeCampaign(
        id: UUID,
        recipients: List<String>,
        context: Map<String, String>,
    ): ExecuteMarketingCampaignResponse {
        val campaign = requireCampaign(id)
        val subject = interpolateTemplate(campaign.subject, context)
        val bodyTemplate =
            campaign.emailTemplateId
                ?.let { emailTemplateRepository.findActiveById(it)?.content }
                ?: campaign.description.orEmpty()
        val body = interpolateTemplate(bodyTemplate, context)
        val sanitizedBody = HtmlSanitizer.sanitize(body)
        val distinct = recipients.filter { it.isNotBlank() }.distinct()
        distinct.forEach { recipient ->
            sendJobRepository.save(
                MarketingSendJob().apply {
                    this.campaignId = campaign.id!!
                    this.recipient = recipient
                    this.subject = subject
                    this.body = sanitizedBody
                },
            )
        }
        return ExecuteMarketingCampaignResponse(
            campaignId = campaign.id!!,
            requested = recipients.size,
            sent = 0,
            queued = distinct.size,
        )
    }

    private fun requireEvent(id: UUID): MarketingEvent =
        eventRepository.findActiveById(id) ?: throw NoSuchElementException("Marketing event not found: $id")

    private fun requireCampaign(id: UUID): MarketingCampaign =
        campaignRepository.findActiveById(id) ?: throw NoSuchElementException("Marketing campaign not found: $id")
}

fun MarketingEvent.toResponse() =
    MarketingEventResponse(
        id = id!!,
        name = name,
        description = description,
        eventDate = eventDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun MarketingCampaign.toResponse() =
    MarketingCampaignResponse(
        id = id!!,
        name = name,
        subject = subject,
        description = description,
        eventId = eventId,
        emailTemplateId = emailTemplateId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
