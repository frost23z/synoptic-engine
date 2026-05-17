package com.synopticengine.api.crm.lead.web

import com.synopticengine.api.crm.lead.domain.LeadStatus
import com.synopticengine.api.crm.tag.web.TagResponse
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateLeadRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    val description: String? = null,
    val amount: BigDecimal? = null,
    val expectedCloseDate: LocalDate? = null,
    val pipelineId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010"),
    val stageId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011"),
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
    val stageId: UUID,
    val status: LeadStatus? = null,
    val lostReason: String? = null,
)

data class MassUpdateLeadRequest(
    val ids: List<UUID>,
    val userId: UUID? = null,
    val stageId: UUID? = null,
    val status: LeadStatus? = null,
)

data class MassDestroyLeadRequest(
    val ids: List<UUID>,
)

data class TagAttachLeadRequest(
    val tagId: UUID,
)

data class EmailAttachLeadRequest(
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
    val productId: UUID,
    val quantity: Int = 1,
    val unitPrice: BigDecimal? = null,
)

data class LeadProductResponse(
    val id: UUID,
    val leadId: UUID,
    val productId: UUID,
    val quantity: Int,
    val unitPrice: BigDecimal?,
)
