package com.synopticengine.api.crm.lead.web

import com.synopticengine.api.crm.lead.domain.LeadStatus
import com.synopticengine.api.crm.tag.web.TagResponse
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateLeadRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    @field:DecimalMin(value = "0.00", message = "Amount must be non-negative")
    val amount: BigDecimal? = null,
    val expectedCloseDate: LocalDate? = null,
    val pipelineId: UUID? = null,
    val stageId: UUID? = null,
    val personId: UUID? = null,
    val organizationId: UUID? = null,
    val leadSourceId: UUID? = null,
    val leadTypeId: UUID? = null,
    val userId: UUID? = null,
)

data class UpdateLeadRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    @field:DecimalMin(value = "0.00", message = "Amount must be non-negative")
    val amount: BigDecimal? = null,
    val expectedCloseDate: LocalDate? = null,
    val status: LeadStatus? = null,
    val lostReason: String? = null,
    val pipelineId: UUID? = null,
    val stageId: UUID? = null,
    val personId: UUID? = null,
    val organizationId: UUID? = null,
    val leadSourceId: UUID? = null,
    val leadTypeId: UUID? = null,
    val userId: UUID? = null,
)

data class MoveStageRequest(
    @field:NotNull(message = "Stage ID is required")
    val stageId: UUID,
    val status: LeadStatus? = null,
    val lostReason: String? = null,
)

data class ConvertLeadRequest(
    @field:NotBlank(message = "First name is required")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    val lastName: String,
    @field:Email(message = "Invalid email address")
    val email: String? = null,
    @field:Size(max = 50, message = "Phone must not exceed 50 characters")
    val phone: String? = null,
    @field:Size(max = 255, message = "Job title must not exceed 255 characters")
    val jobTitle: String? = null,
    val organizationId: UUID? = null,
    @field:Size(max = 255, message = "Organization name must not exceed 255 characters")
    val organizationName: String? = null,
    val closeAsWon: Boolean = true,
)

data class ConvertLeadResponse(
    val leadId: UUID,
    val personId: UUID,
    val organizationId: UUID?,
    val status: String,
)

data class MassUpdateLeadRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot update more than $MAX_BATCH_SIZE leads at once")
    val ids: List<UUID>,
    val userId: UUID? = null,
    val stageId: UUID? = null,
    val status: LeadStatus? = null,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class MassDestroyLeadRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE leads at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class TagAttachLeadRequest(
    @field:NotNull(message = "Tag ID is required")
    val tagId: UUID,
)

data class EmailAttachLeadRequest(
    @field:NotNull(message = "Email ID is required")
    val emailId: UUID,
)

data class LeadResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val amount: BigDecimal?,
    val expectedCloseDate: LocalDate?,
    val status: String,
    val lostReason: String?,
    val closedAt: Instant?,
    val pipelineId: UUID,
    val stageId: UUID,
    val personId: UUID?,
    val organizationId: UUID?,
    val leadSourceId: UUID?,
    val leadTypeId: UUID?,
    val userId: UUID?,
    val tags: List<TagResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class KanbanStageGroup(
    val stage: StageResponse,
    val leads: List<LeadResponse>,
    val totalAmount: BigDecimal,
)

data class AddLeadProductRequest(
    @field:NotNull(message = "Product ID is required")
    val productId: UUID,
    @field:Min(value = 1, message = "Quantity must be at least 1")
    val quantity: Int = 1,
    @field:DecimalMin(value = "0.00", message = "Unit price must be non-negative")
    val unitPrice: BigDecimal? = null,
)

data class LeadProductResponse(
    val id: UUID,
    val leadId: UUID,
    val productId: UUID,
    val quantity: Int,
    val unitPrice: BigDecimal?,
)

data class LookupItem(
    val id: UUID,
    val name: String,
)

data class StageLookupItem(
    val id: UUID,
    val name: String,
    val color: String?,
)

data class KanbanLookupResponse(
    val users: List<LookupItem>,
    val leadSources: List<LookupItem>,
    val leadTypes: List<LookupItem>,
    val stages: List<StageLookupItem>,
)

data class AttributeValueInput(
    @field:NotNull(message = "Attribute ID is required")
    val attributeId: UUID,
    val value: String?,
)

data class UpdateLeadAttributesRequest(
    @field:NotEmpty(message = "Attribute values list must not be empty")
    val attributeValues: List<AttributeValueInput>,
)
