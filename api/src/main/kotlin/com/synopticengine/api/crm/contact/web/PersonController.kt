package com.synopticengine.api.crm.contact.web

import com.synopticengine.api.crm.contact.service.PersonService
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
@RequestMapping($$"${api.base-path}/contacts/persons")
class PersonController(
    private val personService: PersonService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('contacts.view')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<PersonResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return ResponseEntity.ok(personService.findAll(pageable))
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('contacts.view')")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<PersonResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return ResponseEntity.ok(personService.search(q, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('contacts.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<PersonResponse> = ResponseEntity.ok(personService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('contacts.edit')")
    fun create(
        @Valid @RequestBody request: CreatePersonRequest,
    ): ResponseEntity<PersonResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                personService.create(
                    request.firstName,
                    request.lastName,
                    request.organizationId,
                    request.email,
                    request.phone,
                    request.jobTitle,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('contacts.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdatePersonRequest,
    ): ResponseEntity<PersonResponse> =
        ResponseEntity.ok(
            personService.update(
                id,
                request.firstName,
                request.lastName,
                request.organizationId,
                request.email,
                request.phone,
                request.jobTitle,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contacts.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        personService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('contacts.delete')")
    fun massDestroy(
        @RequestBody request: MassDestroyPersonRequest,
    ): ResponseEntity<Map<String, Any>> {
        val deleted = personService.massDestroy(request.ids)
        val skipped = request.ids - deleted.toSet()
        return ResponseEntity.ok(mapOf("deleted" to deleted, "skipped" to skipped))
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('contacts.edit')")
    fun attachTag(
        @PathVariable id: UUID,
        @RequestBody request: TagAttachRequest,
    ): ResponseEntity<PersonResponse> = ResponseEntity.ok(personService.attachTag(id, request.tagId))

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("hasAuthority('contacts.edit')")
    fun detachTag(
        @PathVariable id: UUID,
        @PathVariable tagId: UUID,
    ): ResponseEntity<PersonResponse> = ResponseEntity.ok(personService.detachTag(id, tagId))
}
