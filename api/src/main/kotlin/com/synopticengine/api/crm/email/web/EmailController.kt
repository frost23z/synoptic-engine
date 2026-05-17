package com.synopticengine.api.crm.email.web

import com.synopticengine.api.crm.email.service.EmailService
import com.synopticengine.api.shared.web.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/mail")
class EmailController(
    private val emailService: EmailService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('mail.view')")
    fun list(
        @RequestParam(defaultValue = "inbox") folder: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<PageResponse<EmailResponse>> = ResponseEntity.ok(emailService.findByFolder(folder, pageable))

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mail.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<EmailResponse> = ResponseEntity.ok(emailService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('mail.edit')")
    fun compose(
        @Valid @RequestBody request: ComposeEmailRequest,
    ): ResponseEntity<EmailResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                emailService.compose(
                    subject = request.subject,
                    to = request.to,
                    cc = request.cc,
                    bcc = request.bcc,
                    body = request.body,
                    personId = request.personId,
                    leadId = request.leadId,
                    parentId = request.parentId,
                    folders = request.folders,
                    send = true,
                ),
            )

    @PatchMapping("/{id}/folder")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun moveFolder(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MoveFolderRequest,
    ): ResponseEntity<EmailResponse> = ResponseEntity.ok(emailService.moveFolder(id, request.folder))

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun markRead(
        @PathVariable id: UUID,
        @RequestBody request: MarkReadRequest,
    ): ResponseEntity<EmailResponse> = ResponseEntity.ok(emailService.markRead(id, request.isRead))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        emailService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-update")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun massUpdate(
        @RequestBody request: MassMoveRequest,
    ): ResponseEntity<Void> {
        emailService.massMoveFolder(request.ids, request.folder)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-mark-read")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun massMarkRead(
        @RequestBody request: MassMarkReadRequest,
    ): ResponseEntity<Void> {
        emailService.massMarkRead(request.ids, request.isRead)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun massDestroy(
        @RequestBody request: MassDestroyMailRequest,
    ): ResponseEntity<Void> {
        emailService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun attachTag(
        @PathVariable id: UUID,
        @RequestBody request: AttachTagRequest,
    ): ResponseEntity<EmailResponse> = ResponseEntity.ok(emailService.attachTag(id, request.tagId))

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("hasAuthority('mail.edit')")
    fun detachTag(
        @PathVariable id: UUID,
        @PathVariable tagId: UUID,
    ): ResponseEntity<EmailResponse> = ResponseEntity.ok(emailService.detachTag(id, tagId))

    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('mail.view')")
    fun downloadAttachment(
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        val (bytes, attachment) = emailService.downloadAttachment(attachmentId)
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(attachment.contentType ?: "application/octet-stream")
        headers.contentDisposition = ContentDisposition.attachment().filename(attachment.attachmentFilename).build()
        return ResponseEntity.ok().headers(headers).body(bytes)
    }

    @PostMapping("/inbound-parse")
    fun inboundParse(
        @RequestBody request: InboundParseRequest,
    ): ResponseEntity<EmailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            emailService.parseInbound(
                from = request.from,
                to = request.to,
                subject = request.subject,
                body = request.body,
            ),
        )
}
