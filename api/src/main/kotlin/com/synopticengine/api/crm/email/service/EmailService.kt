package com.synopticengine.api.crm.email.service

import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.email.domain.Email
import com.synopticengine.api.crm.email.domain.EmailAttachment
import com.synopticengine.api.crm.email.domain.EmailStatus
import com.synopticengine.api.crm.email.repo.EmailAttachmentRepository
import com.synopticengine.api.crm.email.repo.EmailRepository
import com.synopticengine.api.crm.email.web.EmailAttachmentResponse
import com.synopticengine.api.crm.email.web.EmailResponse
import com.synopticengine.api.crm.email.web.EmailThreadResponse
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.tag.repo.TagRepository
import com.synopticengine.api.crm.tag.service.toResponse
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.storage.StorageService
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
@Transactional(readOnly = true)
class EmailService(
    private val emailRepository: EmailRepository,
    private val emailDeliveryService: EmailDeliveryService,
    private val tagRepository: TagRepository,
    private val attachmentRepository: EmailAttachmentRepository,
    private val storageService: StorageService,
    private val personRepository: PersonRepository,
    private val leadRepository: LeadRepository,
) {
    fun findByFolder(
        folder: String,
        pageable: Pageable,
    ): PageResponse<EmailResponse> {
        val tenantId = TenantContext.get() ?: error("TenantContext not set; /mail requires authentication")
        return PageResponse.of(emailRepository.findByFolder(tenantId, folder, pageable)) { it.toResponse() }
    }

    fun findById(id: UUID): EmailResponse = requireEmail(id).toResponse()

    fun findThreadById(id: UUID): EmailThreadResponse {
        val requested = requireEmail(id)
        val rootId = requested.parentId ?: requested.id!!
        val thread = emailRepository.findThreadByRootId(rootId)
        val root = thread.firstOrNull { it.id == rootId } ?: requireEmail(rootId)
        return EmailThreadResponse(
            root = root.toResponse(),
            messages = thread.map { it.toResponse() },
        )
    }

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
        val senderEmail = currentUserEmail()
        val email =
            emailRepository.save(
                Email().apply {
                    this.subject = subject
                    this.body = body
                    this.personId = personId
                    this.leadId = leadId
                    this.parentId = parentId
                    this.folders = effectiveFolders
                    this.status = if (isDraft) EmailStatus.DRAFT else EmailStatus.OUTBOX
                    this.from = senderEmail?.let { mapOf("email" to it) }
                    this.to = listOf(mapOf("email" to to))
                    if (cc != null) this.cc = cc.map { mapOf("email" to it) }
                    if (bcc != null) this.bcc = bcc.map { mapOf("email" to it) }
                },
            )
        // Attach any pre-uploaded files (e.g. from a separate /api/mail/attachments
        // staging endpoint — out of scope for this PR) by id.
        attachmentIds.forEach { aid ->
            // findActiveById is JPQL so the tenant filter applies; the
            // caller-supplied attachment id can therefore not refer to a
            // different tenant's pre-staged file.
            attachmentRepository.findActiveById(aid)?.let { a ->
                a.email = email
                a.emailId = email.id!!
                attachmentRepository.save(a)
            }
        }
        // Also accept fresh multipart uploads bundled with the compose request.
        uploads.forEach { upload ->
            val originalName = upload.originalFilename ?: upload.name
            val safeName = Path.of(originalName).fileName?.toString() ?: "upload.bin"
            val storedPath =
                storageService.store(
                    directory = "emails/${email.id}",
                    filename = "${UUID.randomUUID()}_$safeName",
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
            email.status = EmailStatus.OUTBOX
            email.folders = listOf("outbox")
            emailRepository.save(email)
            val emailId = checkNotNull(email.id) { "Saved email id must not be null" }
            emailDeliveryService.deliver(emailId, to, subject ?: "", body ?: "", cc, bcc)
        }
        // Re-read so the attachments collection is populated for the response.
        val refreshed = requireEmail(checkNotNull(email.id) { "Saved email id must not be null" })
        return refreshed.toResponse()
    }

    /** P3.3: send a previously-saved DRAFT. */
    @Transactional
    fun sendDraft(id: UUID): EmailResponse {
        val email = requireEmail(id)
        if (email.status == EmailStatus.SENT || email.status == EmailStatus.OUTBOX) {
            throw IllegalStateException("Email $id is already ${email.status} and cannot be sent again")
        }
        val to =
            email.to?.firstOrNull()?.get("email")
                ?: email.from?.get("email")
                ?: throw IllegalStateException("Draft has no recipient")
        email.status = EmailStatus.OUTBOX
        email.folders = listOf("outbox")
        emailRepository.save(email)
        val emailId = checkNotNull(email.id) { "Saved draft id must not be null" }
        emailDeliveryService.deliver(
            emailId,
            to,
            email.subject ?: "",
            email.body ?: "",
            email.cc?.mapNotNull { it["email"] },
            email.bcc?.mapNotNull { it["email"] },
        )
        return requireEmail(emailId).toResponse()
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
        val senderEmail = currentUserEmail()
        val forwarded =
            emailRepository.save(
                Email().apply {
                    this.subject = subject
                    this.body = combinedBody
                    this.personId = original.personId
                    this.leadId = original.leadId
                    this.parentId = original.id
                    this.folders = listOf("outbox")
                    this.status = EmailStatus.OUTBOX
                    this.from = senderEmail?.let { mapOf("email" to it) }
                    this.to = listOf(mapOf("email" to to))
                    if (cc != null) this.cc = cc.map { mapOf("email" to it) }
                    if (bcc != null) this.bcc = bcc.map { mapOf("email" to it) }
                },
            )
        val forwardedId = checkNotNull(forwarded.id) { "Saved forwarded email id must not be null" }
        emailDeliveryService.deliver(forwardedId, to, subject, combinedBody, cc, bcc)
        return requireEmail(forwardedId).toResponse()
    }

    @Transactional
    fun reply(
        id: UUID,
        message: String?,
        cc: List<String>?,
        bcc: List<String>?,
        attachmentIds: List<UUID>,
    ): EmailResponse {
        val original = requireEmail(id)
        val to = original.from?.get("email") ?: throw IllegalStateException("Original email has no sender address")
        val subject =
            if ((original.subject ?: "").startsWith("Re:", ignoreCase = true)) {
                original.subject
            } else {
                "Re: ${original.subject.orEmpty()}"
            }
        return compose(
            subject = subject,
            to = to,
            cc = cc,
            bcc = bcc,
            body = message,
            personId = original.personId,
            leadId = original.leadId,
            parentId = original.id,
            folders = listOf("sent"),
            isDraft = false,
            attachmentIds = attachmentIds,
        )
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
        // Load through the tenant-aware finder, then delegate to JpaRepository.delete(entity)
        // so the @SQLDelete soft-delete trigger fires. `deleteById(id)` would
        // skip the filter and let one tenant soft-delete another's email.
        val email = requireEmail(id)
        emailRepository.delete(email)
    }

    @Transactional
    fun massMarkRead(
        ids: List<UUID>,
        isRead: Boolean,
    ) {
        ids.forEach { id ->
            emailRepository.findActiveById(id)?.let { email ->
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
            emailRepository.findActiveById(id)?.let { email ->
                email.folders = listOf(folder)
                emailRepository.save(email)
            }
        }
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            emailRepository.findActiveById(id)?.let { email -> emailRepository.delete(email) }
        }
    }

    @Transactional
    fun attachTag(
        emailId: UUID,
        tagId: UUID,
    ): EmailResponse {
        val email = requireEmail(emailId)
        val tag =
            tagRepository
                .findActiveById(tagId)
                ?: throw NoSuchElementException("Tag not found: $tagId")
        email.tags.add(tag)
        return emailRepository.save(email).toResponse()
    }

    @Transactional
    fun detachTag(
        emailId: UUID,
        tagId: UUID,
    ): EmailResponse {
        val email = requireEmail(emailId)
        email.tags.removeIf { it.id == tagId }
        return emailRepository.save(email).toResponse()
    }

    fun downloadAttachment(attachmentId: UUID): Pair<ByteArray, EmailAttachment> {
        val attachment =
            attachmentRepository.findActiveById(attachmentId)
                ?: throw NoSuchElementException("Attachment not found: $attachmentId")
        val bytes = runCatching { storageService.load(attachment.attachmentPath) }.getOrDefault(ByteArray(0))
        return bytes to attachment
    }

    @Transactional
    fun parseInbound(
        from: String,
        to: String,
        subject: String?,
        body: String?,
        messageId: String?,
        inReplyTo: String?,
        references: List<String>?,
    ): EmailResponse {
        val normalizedReferences = references.orEmpty().filter { it.isNotBlank() }
        val threadMessageIds =
            buildList {
                if (!inReplyTo.isNullOrBlank()) add(inReplyTo.trim())
                addAll(normalizedReferences.map { it.trim() })
            }.distinct()
        val detectedPersonId = extractEntityId(body, "person")
        val detectedLeadId = extractEntityId(body, "lead")
        val tenantId = resolveInboundTenantId(to, threadMessageIds)
        val email =
            TenantContext.runAs(tenantId) {
                val parent =
                    if (threadMessageIds.isEmpty()) {
                        null
                    } else {
                        emailRepository.findThreadParentsByMessageIds(threadMessageIds).firstOrNull()
                    }
                emailRepository.save(
                    Email().apply {
                        this.from = mapOf("email" to from)
                        this.to = listOf(mapOf("email" to to))
                        this.subject = subject
                        this.body = body
                        this.folders = listOf("inbox")
                        this.status = EmailStatus.SENT
                        this.isRead = false
                        this.messageId = messageId
                        this.referenceIds = normalizedReferences
                        this.parentId = parent?.id
                        this.personId =
                            detectedPersonId?.takeIf { personRepository.findActiveById(it) != null } ?: parent?.personId
                        this.leadId =
                            detectedLeadId?.takeIf { leadRepository.findActiveById(it) != null } ?: parent?.leadId
                    },
                )
            }
        return email.toResponse()
    }

    private fun resolveInboundTenantId(
        to: String,
        threadMessageIds: List<String>,
    ): UUID {
        resolveTenantFromRecipient(to)?.let { return it }
        if (threadMessageIds.isNotEmpty()) {
            emailRepository.findTenantIdsByMessageIds(threadMessageIds).firstOrNull()?.let { return it }
        }
        throw IllegalArgumentException("Unable to resolve tenant for inbound email recipient")
    }

    private fun resolveTenantFromRecipient(to: String): UUID? {
        val directHit =
            UUID_REGEX
                .find(to)
                ?.value
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (directHit != null) return directHit
        return EMAIL_REGEX
            .findAll(to)
            .mapNotNull { match ->
                val local = match.groupValues[1].lowercase()
                candidateTenantTokens(local)
                    .asSequence()
                    .mapNotNull { token ->
                        UUID_REGEX
                            .find(token)
                            ?.value
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    }.firstOrNull()
            }.firstOrNull()
    }

    private fun candidateTenantTokens(localPart: String): List<String> {
        val plusPart = localPart.substringAfter('+', "")
        return buildList {
            add(localPart)
            if (plusPart.isNotBlank()) add(plusPart)
            if (localPart.startsWith("tenant-")) add(localPart.removePrefix("tenant-"))
            if (plusPart.startsWith("tenant-")) add(plusPart.removePrefix("tenant-"))
        }
    }

    private fun extractEntityId(
        body: String?,
        key: String,
    ): UUID? {
        if (body.isNullOrBlank()) return null
        val lowerKey = key.lowercase()
        val regex =
            ENTITY_ID_REGEX_BY_KEY.computeIfAbsent(lowerKey) {
                Regex("$lowerKey[#:=\\s]+([0-9a-fA-F-]{36})", RegexOption.IGNORE_CASE)
            }
        val value = regex.find(body)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    private companion object {
        val ENTITY_ID_REGEX_BY_KEY: ConcurrentHashMap<String, Regex> = ConcurrentHashMap()
        val EMAIL_REGEX: Regex = Regex("([a-zA-Z0-9._%+\\-]+)@[a-zA-Z0-9.-]+")
        val UUID_REGEX: Regex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }

    private fun currentUserEmail(): String? =
        SecurityContextHolder
            .getContext()
            .authentication
            ?.name
            ?.takeIf { it.isNotBlank() }

    // Tenant-aware load. JpaRepository.findById bypasses Hibernate's
    // `@Filter("tenantFilter")` because it goes through `EntityManager.find()`
    // rather than a query. `findActiveById` uses JPQL so the filter applies
    // and cross-tenant fetches return null (→ 404 at the controller layer)
    // instead of leaking another tenant's row.
    private fun requireEmail(id: UUID): Email =
        emailRepository.findActiveById(id) ?: throw NoSuchElementException("Email not found: $id")
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
        to = to,
        cc = cc,
        bcc = bcc,
        body = body,
        personId = personId,
        leadId = leadId,
        parentId = parentId,
        messageId = messageId,
        referenceIds = referenceIds,
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
