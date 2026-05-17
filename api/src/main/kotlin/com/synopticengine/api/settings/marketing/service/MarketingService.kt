package com.synopticengine.api.settings.marketing.service

import com.synopticengine.api.settings.marketing.domain.MarketingCampaign
import com.synopticengine.api.settings.marketing.domain.MarketingEvent
import com.synopticengine.api.settings.marketing.repo.MarketingCampaignRepository
import com.synopticengine.api.settings.marketing.repo.MarketingEventRepository
import com.synopticengine.api.settings.marketing.web.MarketingCampaignResponse
import com.synopticengine.api.settings.marketing.web.MarketingEventResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MarketingService(
    private val eventRepository: MarketingEventRepository,
    private val campaignRepository: MarketingCampaignRepository,
) {
    // ── Marketing Events ──────────────────────────────────────────────────

    fun findAllEvents(): List<MarketingEventResponse> = eventRepository.findAll().map { it.toResponse() }

    fun findEventById(id: UUID): MarketingEventResponse =
        eventRepository
            .findById(
                id,
            ).orElseThrow { NoSuchElementException("Marketing event not found: $id") }
            .toResponse()

    @Transactional
    fun createEvent(
        name: String,
        description: String?,
    ): MarketingEventResponse =
        eventRepository
            .save(
                MarketingEvent().apply {
                    this.name = name
                    this.description = description
                },
            ).toResponse()

    @Transactional
    fun updateEvent(
        id: UUID,
        name: String,
        description: String?,
    ): MarketingEventResponse {
        val event =
            eventRepository.findById(id).orElseThrow { NoSuchElementException("Marketing event not found: $id") }
        event.name = name
        event.description = description
        return eventRepository.save(event).toResponse()
    }

    @Transactional
    fun deleteEvent(id: UUID) {
        if (!eventRepository.existsById(id)) throw NoSuchElementException("Marketing event not found: $id")
        eventRepository.deleteById(id)
    }

    @Transactional
    fun massDestroyEvents(ids: List<UUID>) =
        ids.filter { eventRepository.existsById(it) }.forEach { eventRepository.deleteById(it) }

    // ── Marketing Campaigns ───────────────────────────────────────────────

    fun findAllCampaigns(): List<MarketingCampaignResponse> = campaignRepository.findAll().map { it.toResponse() }

    fun findCampaignById(id: UUID): MarketingCampaignResponse =
        campaignRepository
            .findById(id)
            .orElseThrow { NoSuchElementException("Marketing campaign not found: $id") }
            .toResponse()

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
        val campaign =
            campaignRepository.findById(id).orElseThrow { NoSuchElementException("Marketing campaign not found: $id") }
        campaign.name = name
        campaign.subject = subject
        campaign.description = description
        campaign.eventId = eventId
        campaign.emailTemplateId = emailTemplateId
        return campaignRepository.save(campaign).toResponse()
    }

    @Transactional
    fun deleteCampaign(id: UUID) {
        if (!campaignRepository.existsById(id)) throw NoSuchElementException("Marketing campaign not found: $id")
        campaignRepository.deleteById(id)
    }

    @Transactional
    fun massDestroyCampaigns(ids: List<UUID>) =
        ids.filter { campaignRepository.existsById(it) }.forEach { campaignRepository.deleteById(it) }
}

fun MarketingEvent.toResponse() =
    MarketingEventResponse(
        id = id!!,
        name = name,
        description = description,
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
