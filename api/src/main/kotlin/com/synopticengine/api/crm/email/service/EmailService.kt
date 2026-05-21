package com.synopticengine.api.crm.email.service

import com.synopticengine.api.crm.email.domain.Email
import com.synopticengine.api.crm.email.domain.EmailAttachment
import com.synopticengine.api.crm.email.domain.EmailStatus
import com.synopticengine.api.crm.email.repo.EmailAttachmentRepository
import com.synopticengine.api.crm.email.repo.EmailRepository
import com.synopticengine.api.crm.email.web.EmailAttachmentResponse
import com.synopticengine.api.crm.email.web.EmailResponse
import com.synopticengine.api.crm.tag.repo.TagRepository
import com.synopticengine.api.crm.tag.service.toResponse
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.email.MailSenderService
import com.synopticengine.api.shared.storage.StorageService
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
@Transactional(readOnly = true)
class EmailService(
    private val emailRepository: EmailRepository,
    private val mailSenderService: MailSenderService,
    private val tagRepository: TagRepository,
    private val attachmentRepository: EmailAttachmentRepository,
    private val storageService: StorageService,
) {
    fun findByFolder(
        folder: String,
        pageable: Pageable,
    ): PageResponse<EmailResponse> {
        val tenantId = TenantContext.get() ?: error("TenantContext not set; /mail requires authentication")
        return PageResponse.of(emailRepository.findByFolder(tenantId, folder, pageable)) { it.toResponse() }
    }

    fun findById(id: UUID): EmailResponse =
        emailRepository.findById(id).orElseThrow { NoSuchElementException("Email not found: $id") }.toResponse()

    @Transactional
    fun compose(
        subject: String?,
        to: String,
        cc: List<String>?,
        bcc: List<String>?,
        body: String?,
        personId: UUID?,
        leadId: UUID?,
        parentId: UUID?,
        folders: List<String>,
        isDraft: Boolean,
        attachmentIds: List<UUID> = emptyList(),
        uploads: List<MultipartFile> = emptyList(),
    ): EmailResponse {
        // Draft goes to "drafts" folder regardless of what the caller asked for —
        // a draft in "sent" or "inbox" makes no sense for the UI's folder filters.
        val effectiveFolders = if (isDraft) listOf("drafts") else folders.ifEmpty { listOf("sent") }
        val email =
            emailRepository.save(
                Email().apply {
                    this.subject = subject
                    this.body = body
                    this.personId = personId
                    this.leadId = leadId
                    this.parentId = parentId
                    this.folders = effectiveFolders
                    this.status = if (isDraft) EmailStatus.DRAFT else EmailStatus.SENT
                    this.from = mapOf("email" to to)
                    if (cc != null) this.cc = cc.map { mapOf("email" to it) }
                    if (bcc != null) this.bcc = bcc.map { mapOf("email" to it) }
                },
            )
        // Attach any pre-uploaded files (e.g. from a separate /api/mail/attachments
        // staging endpoint — out of scope for this PR) by id.
        attachmentIds.forEach { aid ->
            attachmentRepository.findById(aid).ifPresent { a ->
                a.email = email
                a.emailId = email.id!!
                attachmentRepository.save(a)
            }
        }
        // Also accept fresh multipart uploads bundled with the compose request.
        uploads.forEach { upload ->
            val storedPath =
                storageService.store(
                    directory = "emails/${email.id}",
                    filename = "${UUID.randomUUID()}_${upload.originalFilename ?: upload.name}",
                    bytes = upload.bytes,
                    contentType = upload.contentType ?: "application/octet-stream",
                )
            attachmentRepository.save(
                EmailAttachment().apply {
                    this.email = email
                    this.emailId = email.id!!
                    this.attachmentFilename = upload.originalFilename ?: upload.name
                    this.attachmentPath = storedPath
                    this.contentType = upload.contentType
                    this.size = upload.size
                },
            )
        }
        if (!isDraft) {
            mailSenderService.sendEmail(to, subject ?: "", body ?: "", cc, bcc)
        }
        // Re-read so the attachments collection is populated for the response.
        val refreshed = emailRepository.findById(email.id!!).get()
        return refreshed.toResponse()
    }

    /** P3.3: send a previously-saved DRAFT. */
    @Transactional
    fun sendDraft(id: UUID): EmailResponse {
        val email = requireEmail(id)
        if (email.status == EmailStatus.SENT) {
            throw IllegalStateException("Email $id is already SENT")
        }
        val to = email.from?.get("email") ?: throw IllegalStateException("Draft has no recipient")
        mailSenderService.sendEmail(
            to,
            email.subject ?: "",
            email.body ?: "",
            email.cc?.mapNotNull { it["email"] },
            email.bcc?.mapNotNull { it["email"] },
        )
        email.status = EmailStatus.SENT
        // Drafts move to the sent folder once sent.
        email.folders = listOf("sent")
        return emailRepository.save(email).toResponse()
    }

    /** P3.3: forward an existing email to a new recipient. */
    @Transactional
    fun forward(
        id: UUID,
        to: String,
        message: String?,
        cc: List<String>?,
        bcc: List<String>?,
    ): EmailResponse {
        val original = requireEmail(id)
        val combinedBody =
            buildString {
                if (!message.isNullOrBlank()) {
                    appendLine(message)
                    appendLine()
                }
                appendLine("---------- Forwarded message ----------")
                appendLine("Subject: ${original.subject.orEmpty()}")
                appendLine("From: ${original.from?.get("email").orEmpty()}")
                appendLine()
                appendLine(original.body.orEmpty())
            }
        val subject = "Fwd: ${original.subject.orEmpty()}"
        val forwarded =
            emailRepository.save(
                Email().apply {
                    this.subject = subject
                    this.body = combinedBody
                    this.personId = original.personId
                    this.leadId = original.leadId
                    this.parentId = original.id
                    this.folders = listOf("sent")
                    this.status = EmailStatus.SENT
                    this.from = mapOf("email" to to)
                    if (cc != null) this.cc = cc.map { mapOf("email" to it) }
                    if (bcc != null) this.bcc = bcc.map { mapOf("email" to it) }
                },
            )
        mailSenderService.sendEmail(to, subject, combinedBody, cc, bcc)
        return forwarded.toResponse()
    }

    @Transactional
    fun moveFolder(
        id: UUID,
        folder: String,
    ): EmailResponse {
        val email = requireEmail(id)
        email.folders = listOf(folder)
        return emailRepository.save(email).toResponse()
    }

    @Transactional
    fun markRead(
        id: UUID,
        isRead: Boolean,
    ): EmailResponse {
        val email = requireEmail(id)
        email.isRead = isRead
        return emailRepository.save(email).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        if (!emailRepository.existsById(id)) throw NoSuchElementException("Email not found: $id")
        emailRepository.deleteById(id)
    }

    @Transactional
    fun massMarkRead(
        ids: List<UUID>,
        isRead: Boolean,
    ) {
        ids.forEach { id ->
            emailRepository.findById(id).ifPresent { email ->
                email.isRead = isRead
                emailRepository.save(email)
            }
        }
    }

    @Transactional
    fun massMoveFolder(
        ids: List<UUID>,
        folder: String,
    ) {
        ids.forEach { id ->
            emailRepository.findById(id).ifPresent { email ->
                email.folders = listOf(folder)
                emailRepository.save(email)
            }
        }
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            if (emailRepository.existsById(id)) emailRepository.deleteById(id)
        }
    }

    @Transactional
    fun attachTag(
        emailId: UUID,
        tagId: UUID,
    ): EmailResponse {
        val email =
            emailRepository
                .findById(emailId)
                .orElseThrow { NoSuchElementException("Email not found: $emailId") }
        val tag =
            tagRepository
                .findById(tagId)
                .orElseThrow { NoSuchElementException("Tag not found: $tagId") }
        email.tags.add(tag)
        return emailRepository.save(email).toResponse()
    }

    @Transactional
    fun detachTag(
        emailId: UUID,
        tagId: UUID,
    ): EmailResponse {
        val email =
            emailRepository
                .findById(emailId)
                .orElseThrow { NoSuchElementException("Email not found: $emailId") }
        email.tags.removeIf { it.id == tagId }
        return emailRepository.save(email).toResponse()
    }

    fun downloadAttachment(attachmentId: UUID): Pair<ByteArray, EmailAttachment> {
        val attachment =
            attachmentRepository
                .findById(attachmentId)
                .orElseThrow { NoSuchElementException("Attachment not found: $attachmentId") }
        val path =
            java.nio.file.Path
                .of(attachment.attachmentPath)
        val bytes =
            if (java.nio.file.Files
                    .exists(path)
            ) {
                java.nio.file.Files
                    .readAllBytes(path)
            } else {
                ByteArray(0)
            }
        return bytes to attachment
    }

    @Transactional
    fun parseInbound(
        from: String,
        to: String,
        subject: String?,
        body: String?,
    ): EmailResponse {
        // The inbound endpoint is public, so no JWT has populated TenantContext.
        // Until Phase 2 resolves the tenant from the recipient address, land the
        // email in the seed tenant so BaseEntity.@PrePersist has a tenant to assign.
        val email =
            TenantContext.runAs(TenantContext.SEED_TENANT_ID) {
                emailRepository.save(
                    Email().apply {
                        this.from = mapOf("email" to from)
                        this.subject = subject
                        this.body = body
                        this.folders = listOf("inbox")
                        this.status = EmailStatus.SENT
                        this.isRead = false
                    },
                )
            }
        return email.toResponse()
    }

    private fun requireEmail(id: UUID): Email =
        emailRepository.findById(id).orElseThrow { NoSuchElementException("Email not found: $id") }
}

fun Email.toResponse() =
    EmailResponse(
        id = id!!,
        subject = subject,
        name = name,
        isRead = isRead,
        status = status.name,
        folders = folders,
        from = from,
        cc = cc,
        bcc = bcc,
        body = body,
        personId = personId,
        leadId = leadId,
        parentId = parentId,
        attachments = attachments.map { it.toResponse() },
        tags = tags.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun EmailAttachment.toResponse() =
    EmailAttachmentResponse(
        id = id!!,
        attachmentFilename = attachmentFilename,
        contentType = contentType,
        size = size,
    )
