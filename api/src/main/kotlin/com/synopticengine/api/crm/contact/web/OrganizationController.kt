package com.synopticengine.api.crm.contact.web

import com.synopticengine.api.crm.activity.service.ActivityService
import com.synopticengine.api.crm.activity.web.ActivityResponse
import com.synopticengine.api.crm.contact.service.OrganizationService
import com.synopticengine.api.shared.web.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/contacts/organizations")
class OrganizationController(
    private val organizationService: OrganizationService,
    private val activityService: ActivityService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('contacts.view', 'contacts.organizations.view')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<OrganizationResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return ResponseEntity.ok(organizationService.findAll(pageable))
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('contacts.view', 'contacts.organizations.view')")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<OrganizationResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return ResponseEntity.ok(organizationService.search(q, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('contacts.view', 'contacts.organizations.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<OrganizationResponse> = ResponseEntity.ok(organizationService.findById(id))

    @PostMapping
    @PreAuthorize("hasAnyAuthority('contacts.create', 'contacts.organizations.create')")
    fun create(
        @Valid @RequestBody request: CreateOrganizationRequest,
    ): ResponseEntity<OrganizationResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                organizationService.create(
                    request.name,
                    request.email,
                    request.phone,
                    request.website,
                    request.address,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('contacts.edit', 'contacts.organizations.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateOrganizationRequest,
    ): ResponseEntity<OrganizationResponse> =
        ResponseEntity.ok(
            organizationService.update(
                id,
                request.name,
                request.email,
                request.phone,
                request.website,
                request.address,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('contacts.delete', 'contacts.organizations.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        organizationService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAnyAuthority('contacts.delete', 'contacts.organizations.delete')")
    fun massDestroy(
        @Valid @RequestBody request: MassDestroyOrganizationRequest,
    ): ResponseEntity<Void> {
        organizationService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAnyAuthority('contacts.view', 'contacts.organizations.view')")
    fun getActivities(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ActivityResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("scheduleFrom").descending())
        return ResponseEntity.ok(
            activityService.filter(
                leadId = null,
                personId = null,
                organizationId = id,
                userId = null,
                type = null,
                isDone = null,
                productId = null,
                warehouseId = null,
                pageable = pageable,
            ),
        )
    }
}
