package com.synopticengine.api.settings.emailtemplate.web

import com.synopticengine.api.settings.emailtemplate.service.EmailTemplateService
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
@RequestMapping($$"${api.base-path}/settings/email-templates")
class EmailTemplateController(
    private val emailTemplateService: EmailTemplateService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('settings.view')")
    fun listAll(): ResponseEntity<List<EmailTemplateResponse>> = ResponseEntity.ok(emailTemplateService.findAll())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<EmailTemplateResponse> = ResponseEntity.ok(emailTemplateService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('settings.edit')")
    fun create(
        @Valid @RequestBody request: CreateEmailTemplateRequest,
    ): ResponseEntity<EmailTemplateResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(emailTemplateService.create(request.name, request.subject, request.content))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateEmailTemplateRequest,
    ): ResponseEntity<EmailTemplateResponse> =
        ResponseEntity.ok(emailTemplateService.update(id, request.name, request.subject, request.content))

    @PostMapping("/{id}/render")
    @PreAuthorize("hasAuthority('settings.view')")
    fun render(
        @PathVariable id: UUID,
        @RequestBody request: RenderEmailTemplateRequest,
    ): ResponseEntity<EmailTemplateResponse> = ResponseEntity.ok(emailTemplateService.render(id, request.context))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        emailTemplateService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
