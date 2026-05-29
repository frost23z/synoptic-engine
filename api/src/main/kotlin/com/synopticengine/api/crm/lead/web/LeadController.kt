package com.synopticengine.api.crm.lead.web

import com.synopticengine.api.crm.activity.service.ActivityService
import com.synopticengine.api.crm.activity.web.ActivityResponse
import com.synopticengine.api.crm.email.web.EmailResponse
import com.synopticengine.api.crm.lead.domain.LeadStatus
import com.synopticengine.api.crm.lead.service.LeadService
import com.synopticengine.api.crm.quote.service.QuoteService
import com.synopticengine.api.crm.quote.web.QuoteResponse
import com.synopticengine.api.shared.attribute.EntityAttributeValueSummary
import com.synopticengine.api.shared.web.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/leads")
class LeadController(
    private val leadService: LeadService,
    private val activityService: ActivityService,
    private val quoteService: QuoteService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('leads.view')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam pipelineId: UUID?,
        @RequestParam stageId: UUID?,
        @RequestParam status: LeadStatus?,
        @RequestParam userId: UUID?,
    ): ResponseEntity<PageResponse<LeadResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return if (pipelineId != null) {
            ResponseEntity.ok(leadService.filter(pipelineId, stageId, status, userId, pageable))
        } else {
            ResponseEntity.ok(leadService.findAll(pageable))
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('leads.view')")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<LeadResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return ResponseEntity.ok(leadService.search(q, pageable))
    }

    @GetMapping("/rotten")
    @PreAuthorize("hasAuthority('leads.view')")
    fun rotten(
        @RequestParam pipelineId: UUID?,
    ): ResponseEntity<List<LeadResponse>> = ResponseEntity.ok(leadService.findRottenLeads(pipelineId))

    @GetMapping("/kanban")
    @PreAuthorize("hasAuthority('leads.view')")
    fun kanban(
        @RequestParam pipelineId: UUID,
    ): ResponseEntity<List<KanbanStageGroup>> = ResponseEntity.ok(leadService.kanban(pipelineId))

    @GetMapping("/kanban/lookup")
    @PreAuthorize("hasAuthority('leads.view')")
    fun kanbanLookup(
        @RequestParam pipelineId: UUID,
    ): ResponseEntity<KanbanLookupResponse> = ResponseEntity.ok(leadService.kanbanLookup(pipelineId))

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<LeadResponse> = ResponseEntity.ok(leadService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('leads.create')")
    fun create(
        @Valid @RequestBody request: CreateLeadRequest,
    ): ResponseEntity<LeadResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                leadService.create(
                    request.title,
                    request.description,
                    request.amount,
                    request.expectedCloseDate,
                    request.pipelineId,
                    request.stageId,
                    request.personId,
                    request.organizationId,
                    request.leadSourceId,
                    request.leadTypeId,
                    request.userId,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateLeadRequest,
    ): ResponseEntity<LeadResponse> =
        ResponseEntity.ok(
            leadService.update(
                id,
                request.title,
                request.description,
                request.amount,
                request.expectedCloseDate,
                request.status,
                request.lostReason,
                request.pipelineId,
                request.stageId,
                request.personId,
                request.organizationId,
                request.leadSourceId,
                request.leadTypeId,
                request.userId,
            ),
        )

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun moveStage(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MoveStageRequest,
    ): ResponseEntity<LeadResponse> =
        ResponseEntity.ok(leadService.moveStage(id, request.stageId, request.status, request.lostReason))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        leadService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-update")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun massUpdate(
        @Valid @RequestBody request: MassUpdateLeadRequest,
    ): ResponseEntity<Void> {
        leadService.massUpdate(request.ids, request.userId, request.stageId, request.status)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('leads.delete')")
    fun massDestroy(
        @Valid @RequestBody request: MassDestroyLeadRequest,
    ): ResponseEntity<Void> {
        leadService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun attachTag(
        @PathVariable id: UUID,
        @Valid @RequestBody request: TagAttachLeadRequest,
    ): ResponseEntity<LeadResponse> = ResponseEntity.ok(leadService.attachTag(id, request.tagId))

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun detachTag(
        @PathVariable id: UUID,
        @PathVariable tagId: UUID,
    ): ResponseEntity<LeadResponse> = ResponseEntity.ok(leadService.detachTag(id, tagId))

    @GetMapping("/{id}/emails")
    @PreAuthorize("hasAuthority('leads.view')")
    fun listEmails(
        @PathVariable id: UUID,
    ): ResponseEntity<List<EmailResponse>> = ResponseEntity.ok(leadService.findEmails(id))

    @PostMapping("/{id}/emails")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun attachEmail(
        @PathVariable id: UUID,
        @Valid @RequestBody request: EmailAttachLeadRequest,
    ): ResponseEntity<LeadResponse> = ResponseEntity.ok(leadService.attachEmail(id, request.emailId))

    @DeleteMapping("/{id}/emails/{emailId}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun detachEmail(
        @PathVariable id: UUID,
        @PathVariable emailId: UUID,
    ): ResponseEntity<LeadResponse> = ResponseEntity.ok(leadService.detachEmail(id, emailId))

    @GetMapping("/{id}/products")
    @PreAuthorize("hasAuthority('leads.view')")
    fun listProducts(
        @PathVariable id: UUID,
    ): ResponseEntity<List<LeadProductResponse>> = ResponseEntity.ok(leadService.listProducts(id))

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('leads.view')")
    fun getActivities(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ActivityResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("scheduleFrom").descending())
        return ResponseEntity.ok(
            activityService.filter(
                leadId = id,
                personId = null,
                organizationId = null,
                userId = null,
                type = null,
                isDone = null,
                productId = null,
                warehouseId = null,
                pageable = pageable,
            ),
        )
    }

    @GetMapping("/{id}/quotes")
    @PreAuthorize("hasAnyAuthority('contacts.view', 'quotes.view')")
    fun listQuotes(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<QuoteResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return ResponseEntity.ok(quoteService.filter(leadId = id, status = null, pageable = pageable))
    }

    @PostMapping("/{id}/products")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun addProduct(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddLeadProductRequest,
    ): ResponseEntity<LeadProductResponse> =
        ResponseEntity.ok(leadService.addProduct(id, request.productId, request.quantity, request.unitPrice))

    @DeleteMapping("/{id}/products/{productId}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun removeProduct(
        @PathVariable id: UUID,
        @PathVariable productId: UUID,
    ): ResponseEntity<Void> {
        leadService.removeProduct(id, productId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{id}/attributes")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun updateAttributes(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateLeadAttributesRequest,
    ): ResponseEntity<List<EntityAttributeValueSummary>> =
        ResponseEntity.ok(
            leadService.updateAttributes(
                id,
                request.attributeValues.map { it.attributeId to it.value },
            ),
        )

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun convert(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ConvertLeadRequest,
    ): ResponseEntity<ConvertLeadResponse> =
        ResponseEntity.ok(
            leadService.convert(
                id = id,
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email,
                phone = request.phone,
                jobTitle = request.jobTitle,
                organizationId = request.organizationId,
                organizationName = request.organizationName,
                closeAsWon = request.closeAsWon,
            ),
        )
}
