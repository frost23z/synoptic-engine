package com.synopticengine.api.crm.contact.web

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
    val email: String? = null,
    val phone: String? = null,
    val jobTitle: String? = null,
)

data class UpdatePersonRequest(
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    val organizationId: UUID? = null,
    val email: String? = null,
    val phone: String? = null,
    val jobTitle: String? = null,
)

data class PersonResponse(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val organizationId: UUID?,
    val email: String?,
    val phone: String?,
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
