package com.synopticengine.api.settings.marketing.web

import com.synopticengine.api.settings.marketing.service.MarketingService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/settings/marketing/events")
class MarketingEventController(
    private val marketingService: MarketingService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('marketing.view')")
    fun listAll(): ResponseEntity<List<MarketingEventResponse>> = ResponseEntity.ok(marketingService.findAllEvents())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<MarketingEventResponse> = ResponseEntity.ok(marketingService.findEventById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('marketing.create')")
    fun create(
        @Valid @RequestBody request: CreateMarketingEventRequest,
    ): ResponseEntity<MarketingEventResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(marketingService.createEvent(request.name, request.description, request.eventDate))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMarketingEventRequest,
    ): ResponseEntity<MarketingEventResponse> =
        ResponseEntity.ok(marketingService.updateEvent(id, request.name, request.description, request.eventDate))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        marketingService.deleteEvent(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('marketing.edit')")
    fun massDestroy(
        @RequestBody request: MassDestroyRequest,
    ): ResponseEntity<Void> {
        marketingService.massDestroyEvents(request.ids)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping($$"${api.base-path}/settings/marketing/campaigns")
class MarketingCampaignController(
    private val marketingService: MarketingService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('marketing.view')")
    fun listAll(): ResponseEntity<List<MarketingCampaignResponse>> =
        ResponseEntity.ok(marketingService.findAllCampaigns())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<MarketingCampaignResponse> = ResponseEntity.ok(marketingService.findCampaignById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('marketing.create')")
    fun create(
        @Valid @RequestBody request: CreateMarketingCampaignRequest,
    ): ResponseEntity<MarketingCampaignResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                marketingService.createCampaign(
                    request.name,
                    request.subject,
                    request.description,
                    request.eventId,
                    request.emailTemplateId,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMarketingCampaignRequest,
    ): ResponseEntity<MarketingCampaignResponse> =
        ResponseEntity.ok(
            marketingService.updateCampaign(
                id,
                request.name,
                request.subject,
                request.description,
                request.eventId,
                request.emailTemplateId,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('marketing.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        marketingService.deleteCampaign(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('marketing.edit')")
    fun massDestroy(
        @RequestBody request: MassDestroyRequest,
    ): ResponseEntity<Void> {
        marketingService.massDestroyCampaigns(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('marketing.edit')")
    fun execute(
        @PathVariable id: UUID,
        @RequestBody request: ExecuteMarketingCampaignRequest,
    ): ResponseEntity<ExecuteMarketingCampaignResponse> =
        ResponseEntity.ok(marketingService.executeCampaign(id, request.recipients, request.context))
}
