package com.synopticengine.api.settings.webform.web

import com.synopticengine.api.settings.webform.service.WebFormService
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
@RequestMapping($$"${api.base-path}/settings/web-forms")
class WebFormController(
    private val webFormService: WebFormService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('settings.view')")
    fun listAll(): ResponseEntity<List<WebFormResponse>> = ResponseEntity.ok(webFormService.findAll())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<WebFormResponse> = ResponseEntity.ok(webFormService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('settings.edit')")
    fun create(
        @Valid @RequestBody request: CreateWebFormRequest,
    ): ResponseEntity<WebFormResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(webFormService.create(request.title, request.description, request.isActive, request.fields))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWebFormRequest,
    ): ResponseEntity<WebFormResponse> =
        ResponseEntity.ok(
            webFormService.update(id, request.title, request.description, request.isActive, request.fields),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        webFormService.delete(id)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("/web-forms")
class PublicWebFormController(
    private val webFormService: WebFormService,
) {
    @GetMapping("/{id}")
    fun getPublicForm(
        @PathVariable id: UUID,
    ): ResponseEntity<WebFormResponse> = ResponseEntity.ok(webFormService.findPublicById(id))

    @PostMapping("/{id}/submit")
    fun submit(
        @PathVariable id: UUID,
        @RequestBody request: WebFormSubmitRequest,
    ): ResponseEntity<WebFormSubmitResponse> {
        // Validate the form exists and is active
        webFormService.findPublicById(id)
        return ResponseEntity.ok(WebFormSubmitResponse(success = true, message = "Form submitted successfully"))
    }
}
