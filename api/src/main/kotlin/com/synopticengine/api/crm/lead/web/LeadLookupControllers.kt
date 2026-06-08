package com.synopticengine.api.crm.lead.web

import com.synopticengine.api.crm.lead.service.LeadSourceService
import com.synopticengine.api.crm.lead.service.LeadTypeService
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
@RequestMapping("/lead-sources")
class LeadSourceController(
    private val leadSourceService: LeadSourceService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('leads.view')")
    fun listAll(): ResponseEntity<List<LeadSourceResponse>> = ResponseEntity.ok(leadSourceService.findAll())

    @PostMapping
    @PreAuthorize("hasAuthority('leads.edit')")
    fun create(
        @Valid @RequestBody request: CreateLeadSourceRequest,
    ): ResponseEntity<LeadSourceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(leadSourceService.create(request.name))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateLeadSourceRequest,
    ): ResponseEntity<LeadSourceResponse> = ResponseEntity.ok(leadSourceService.update(id, request.name))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        leadSourceService.delete(id)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("/lead-types")
class LeadTypeController(
    private val leadTypeService: LeadTypeService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('leads.view')")
    fun listAll(): ResponseEntity<List<LeadTypeResponse>> = ResponseEntity.ok(leadTypeService.findAll())

    @PostMapping
    @PreAuthorize("hasAuthority('leads.edit')")
    fun create(
        @Valid @RequestBody request: CreateLeadTypeRequest,
    ): ResponseEntity<LeadTypeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(leadTypeService.create(request.name))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateLeadTypeRequest,
    ): ResponseEntity<LeadTypeResponse> = ResponseEntity.ok(leadTypeService.update(id, request.name))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        leadTypeService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
