package com.synopticengine.api.crm.contact.web

import com.synopticengine.api.crm.contact.domain.ContactEntry
import com.synopticengine.api.crm.tag.web.TagResponse
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

// ── Organization ──────────────────────────────────────────────────────────────

data class CreateOrganizationRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val address: String? = null,
)

data class UpdateOrganizationRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val address: String? = null,
)

data class OrganizationResponse(
    val id: UUID,
    val name: String,
    val email: String?,
    val phone: String?,
    val website: String?,
    val address: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

// ── Person ────────────────────────────────────────────────────────────────────

data class CreatePersonRequest(
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    val organizationId: UUID? = null,
    /** Legacy single-email shortcut; ignored when [emails] is provided. */
    val email: String? = null,
    /** Preferred — `[{value, label}]` array. Wins over [email] when both are set. */
    val emails: List<ContactEntry>? = null,
    /** Legacy single-phone shortcut; ignored when [contactNumbers] is provided. */
    val phone: String? = null,
    /** Preferred — `[{value, label}]` array. Wins over [phone] when both are set. */
    val contactNumbers: List<ContactEntry>? = null,
    val jobTitle: String? = null,
)

data class UpdatePersonRequest(
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    val organizationId: UUID? = null,
    val email: String? = null,
    val emails: List<ContactEntry>? = null,
    val phone: String? = null,
    val contactNumbers: List<ContactEntry>? = null,
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
    val tagId: UUID,
)

data class MassDestroyPersonRequest(
    val ids: List<UUID>,
)

data class MassDestroyOrganizationRequest(
    val ids: List<UUID>,
)
