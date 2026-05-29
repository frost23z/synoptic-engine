package com.synopticengine.api.crm.contact.web

import com.synopticengine.api.crm.contact.domain.ContactEntry
import com.synopticengine.api.crm.tag.web.TagResponse
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import java.time.Instant
import java.util.UUID

// ── Organization ──────────────────────────────────────────────────────────────

data class CreateOrganizationRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:Email(message = "Invalid email address")
    val email: String? = null,
    @field:Size(max = 50, message = "Phone must not exceed 50 characters")
    val phone: String? = null,
    @field:URL(message = "Website must be a valid URL")
    val website: String? = null,
    @field:Size(max = 500, message = "Address must not exceed 500 characters")
    val address: String? = null,
)

data class UpdateOrganizationRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:Email(message = "Invalid email address")
    val email: String? = null,
    @field:Size(max = 50, message = "Phone must not exceed 50 characters")
    val phone: String? = null,
    @field:URL(message = "Website must be a valid URL")
    val website: String? = null,
    @field:Size(max = 500, message = "Address must not exceed 500 characters")
    val address: String? = null,
)

data class OrganizationResponse(
    val id: UUID,
    val name: String,
    val email: String?,
    val phone: String?,
    val website: String?,
    val address: String?,
    val tags: List<TagResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class TagAttachOrganizationRequest(
    @field:NotNull(message = "Tag ID is required")
    val tagId: UUID,
)

// ── Person ────────────────────────────────────────────────────────────────────

data class CreatePersonRequest(
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    val organizationId: UUID? = null,
    /**
     * Legacy single-email shortcut; ignored when [emails] is provided.
     * Must be a valid RFC 5321 address when supplied.
     */
    @field:Email(message = "Invalid email address")
    val email: String? = null,
    /** Preferred — `[{value, label}]` array. Wins over [email] when both are set. */
    @field:Size(max = 20, message = "Cannot store more than 20 email addresses")
    val emails: List<ContactEntry>? = null,
    /** Legacy single-phone shortcut; ignored when [contactNumbers] is provided. */
    @field:Size(max = 50, message = "Phone must not exceed 50 characters")
    val phone: String? = null,
    /** Preferred — `[{value, label}]` array. Wins over [phone] when both are set. */
    @field:Size(max = 20, message = "Cannot store more than 20 phone numbers")
    val contactNumbers: List<ContactEntry>? = null,
    @field:Size(max = 255, message = "Job title must not exceed 255 characters")
    val jobTitle: String? = null,
)

data class UpdatePersonRequest(
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    val organizationId: UUID? = null,
    @field:Email(message = "Invalid email address")
    val email: String? = null,
    @field:Size(max = 20, message = "Cannot store more than 20 email addresses")
    val emails: List<ContactEntry>? = null,
    @field:Size(max = 50, message = "Phone must not exceed 50 characters")
    val phone: String? = null,
    @field:Size(max = 20, message = "Cannot store more than 20 phone numbers")
    val contactNumbers: List<ContactEntry>? = null,
    @field:Size(max = 255, message = "Job title must not exceed 255 characters")
    val jobTitle: String? = null,
)

data class PersonResponse(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val organizationId: UUID?,
    /** Convenience: first email value, falling back to the legacy scalar column. */
    val email: String?,
    val emails: List<ContactEntry>,
    /** Convenience: first phone value, falling back to the legacy scalar column. */
    val phone: String?,
    val contactNumbers: List<ContactEntry>,
    val jobTitle: String?,
    val tags: List<TagResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class TagAttachRequest(
    @field:NotNull(message = "Tag ID is required")
    val tagId: UUID,
)

data class MassDestroyPersonRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE persons at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class MassDestroyOrganizationRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE organizations at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class MergePersonRequest(
    val sourceId: UUID,
    val targetId: UUID,
)

data class MergePersonResponse(
    val merged: Boolean,
    val targetId: UUID,
)
