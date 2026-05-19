package com.synopticengine.api.sharing.web

import com.synopticengine.api.sharing.domain.RelationshipType
import com.synopticengine.api.sharing.domain.TenantRelationship
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateRelationshipRequest(
    @field:NotNull
    val targetTenantId: UUID?,
    @field:NotNull
    val type: RelationshipType?,
    @field:Size(max = 2000)
    val note: String? = null,
)

data class RelationshipResponse(
    val id: UUID,
    val sourceTenantId: UUID,
    val targetTenantId: UUID,
    val type: RelationshipType,
    val status: String,
    val initiatedBy: UUID,
    val acceptedBy: UUID?,
    val note: String?,
    val createdAt: Instant?,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
) {
    companion object {
        fun from(rel: TenantRelationship) =
            RelationshipResponse(
                id = rel.id!!,
                sourceTenantId = rel.sourceTenantId,
                targetTenantId = rel.targetTenantId,
                type = rel.relationshipType,
                status = rel.status.name,
                initiatedBy = rel.initiatedBy,
                acceptedBy = rel.acceptedBy,
                note = rel.note,
                createdAt = rel.createdAt,
                acceptedAt = rel.acceptedAt,
                revokedAt = rel.revokedAt,
            )
    }
}
