package com.synopticengine.api.crm.tag.web

import com.synopticengine.api.crm.tag.service.TagService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/tags")
class TagController(
    private val tagService: TagService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('leads.view')")
    fun listAll(): ResponseEntity<List<TagResponse>> = ResponseEntity.ok(tagService.findAll())

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('leads.view')")
    fun search(
        @RequestParam q: String,
    ): ResponseEntity<List<TagResponse>> = ResponseEntity.ok(tagService.search(q))

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<TagResponse> = ResponseEntity.ok(tagService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('leads.edit')")
    fun create(
        @Valid @RequestBody request: CreateTagRequest,
    ): ResponseEntity<TagResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(tagService.create(request.name, request.color))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTagRequest,
    ): ResponseEntity<TagResponse> = ResponseEntity.ok(tagService.update(id, request.name, request.color))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        tagService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun massDestroy(
        @RequestBody request: MassDestroyTagRequest,
    ): ResponseEntity<Void> {
        tagService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }
}
