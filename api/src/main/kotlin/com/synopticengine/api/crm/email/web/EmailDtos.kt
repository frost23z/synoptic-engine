package com.synopticengine.api.crm.email.web

import com.synopticengine.api.crm.tag.web.TagResponse
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class EmailAttachmentResponse(
    val id: UUID,
    val attachmentFilename: String,
    val contentType: String?,
    val size: Long?,
)

data class EmailResponse(
    val id: UUID,
    val subject: String?,
    val name: String?,
    val isRead: Boolean,
    val status: String,
    val folders: List<String>,
    val from: Map<String, String>?,
    val cc: List<Map<String, String>>?,
    val bcc: List<Map<String, String>>?,
    val body: String?,
    val personId: UUID?,
    val leadId: UUID?,
    val parentId: UUID?,
    val messageId: String?,
    val referenceIds: List<String>?,
    val attachments: List<EmailAttachmentResponse>,
    val tags: List<TagResponse> = emptyList(),
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class ComposeEmailRequest(
    val subject: String?,
    @field:NotBlank val to: String,
    val cc: List<String>? = null,
    val bcc: List<String>? = null,
    val body: String?,
    val personId: UUID? = null,
    val leadId: UUID? = null,
    val parentId: UUID? = null,
    val folders: List<String> = listOf("sent"),
    /** P3.3: when true, the email is saved as DRAFT (and not actually sent). */
    val isDraft: Boolean = false,
    /** P3.7: pre-uploaded attachment ids to associate with this email. */
    val attachmentIds: List<UUID> = emptyList(),
)

data class ForwardEmailRequest(
    @field:NotBlank val to: String,
    val message: String? = null,
    val cc: List<String>? = null,
    val bcc: List<String>? = null,
)

data class MoveFolderRequest(
    @field:NotBlank val folder: String,
)

data class MarkReadRequest(
    val isRead: Boolean,
)

data class MassMarkReadRequest(
    val ids: List<UUID>,
    val isRead: Boolean,
)

data class MassMoveRequest(
    val ids: List<UUID>,
    @field:NotBlank val folder: String,
)

data class MassDestroyMailRequest(
    val ids: List<UUID>,
)

data class AttachTagRequest(
    val tagId: UUID,
)

data class InboundParseRequest(
    val from: String,
    val to: String,
    val subject: String? = null,
    val body: String? = null,
    val messageId: String? = null,
    val inReplyTo: String? = null,
    val references: List<String>? = null,
)
