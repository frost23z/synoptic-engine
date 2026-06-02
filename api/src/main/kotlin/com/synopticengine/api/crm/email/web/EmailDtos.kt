package com.synopticengine.api.crm.email.web

import com.synopticengine.api.crm.tag.web.TagResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class MailFolderResponse(
    val folder: String,
    val permissionKey: String,
    val label: String,
)

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
    val to: List<Map<String, String>>?,
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

data class EmailThreadResponse(
    val root: EmailResponse,
    val messages: List<EmailResponse>,
)

data class ComposeEmailRequest(
    @field:Size(max = 500, message = "Subject must not exceed 500 characters")
    val subject: String?,
    @field:NotBlank(message = "Recipient address is required")
    @field:Size(max = 500, message = "To address must not exceed 500 characters")
    val to: String,
    @field:Size(max = 50, message = "Cannot have more than 50 CC recipients")
    val cc: List<String>? = null,
    @field:Size(max = 50, message = "Cannot have more than 50 BCC recipients")
    val bcc: List<String>? = null,
    @field:Size(max = 500_000, message = "Email body must not exceed 500 000 characters")
    val body: String?,
    val personId: UUID? = null,
    val leadId: UUID? = null,
    val parentId: UUID? = null,
    @field:Size(max = 20, message = "Cannot add more than 20 folders")
    val folders: List<String> = listOf("sent"),
    /** P3.3: when true, the email is saved as DRAFT (and not actually sent). */
    val isDraft: Boolean = false,
    /** P3.7: pre-uploaded attachment ids to associate with this email. */
    @field:Size(max = 25, message = "Cannot attach more than 25 files")
    val attachmentIds: List<UUID> = emptyList(),
)

data class ForwardEmailRequest(
    @field:NotBlank(message = "Recipient address is required")
    @field:Size(max = 500, message = "To address must not exceed 500 characters")
    val to: String,
    @field:Size(max = 500_000, message = "Message must not exceed 500 000 characters")
    val message: String? = null,
    @field:Size(max = 50, message = "Cannot have more than 50 CC recipients")
    val cc: List<String>? = null,
    @field:Size(max = 50, message = "Cannot have more than 50 BCC recipients")
    val bcc: List<String>? = null,
)

data class ReplyEmailRequest(
    @field:Size(max = 500_000, message = "Email body must not exceed 500 000 characters")
    val body: String?,
    @field:Size(max = 50, message = "Cannot have more than 50 CC recipients")
    val cc: List<String>? = null,
    @field:Size(max = 50, message = "Cannot have more than 50 BCC recipients")
    val bcc: List<String>? = null,
    @field:Size(max = 25, message = "Cannot attach more than 25 files")
    val attachmentIds: List<UUID> = emptyList(),
)

data class MoveFolderRequest(
    @field:NotBlank val folder: String,
)

data class MarkReadRequest(
    val isRead: Boolean,
)

data class MassMarkReadRequest(
    @field:Size(max = 500, message = "Cannot update more than 500 emails at once")
    val ids: List<UUID>,
    val isRead: Boolean,
)

data class MassMoveRequest(
    @field:Size(max = 500, message = "Cannot move more than 500 emails at once")
    val ids: List<UUID>,
    @field:NotBlank val folder: String,
)

data class MassDestroyMailRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE emails at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class AttachTagRequest(
    @field:NotNull(message = "Tag ID is required")
    val tagId: UUID,
)

data class InboundParseRequest(
    @field:NotBlank(message = "From address is required")
    @field:Size(max = 500, message = "From address must not exceed 500 characters")
    val from: String,
    @field:NotBlank(message = "To address is required")
    @field:Size(max = 500, message = "To address must not exceed 500 characters")
    val to: String,
    @field:Size(max = 500, message = "Subject must not exceed 500 characters")
    val subject: String? = null,
    /** Cap body size before regex/processing to prevent DoS on large payloads. T2.5(b). */
    @field:Size(max = 1_000_000, message = "Email body must not exceed 1 000 000 characters")
    val body: String? = null,
    @field:Size(max = 500, message = "Message-ID must not exceed 500 characters")
    val messageId: String? = null,
    @field:Size(max = 500, message = "In-Reply-To must not exceed 500 characters")
    val inReplyTo: String? = null,
    @field:Size(max = 50, message = "References list must not exceed 50 entries")
    val references: List<String>? = null,
)
