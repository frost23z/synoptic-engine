package com.synopticengine.api.sharing.web

import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.TenantSharePolicy
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateSharePolicyRequest(
    @field:NotBlank
    val resourceType: String?,
    @field:NotNull
    val accessLevel: AccessLevel?,
    val filterJson: String? = null,
    val cascadeJson: String? = null,
    val materialize: Boolean = true,
)

data class UpdateSharePolicyRequest(
    val accessLevel: AccessLevel? = null,
    val filterJson: String? = null,
    val cascadeJson: String? = null,
)

data class SharePolicyResponse(
    val id: UUID,
    val relationshipId: UUID,
    val resourceType: String,
    val accessLevel: AccessLevel,
    val filterJson: String?,
    val cascadeJson: String?,
    val materialize: Boolean,
    val createdBy: UUID,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val revokedAt: Instant?,
) {
    companion object {
        fun from(p: TenantSharePolicy) =
            SharePolicyResponse(
                id = p.id!!,
                relationshipId = p.relationshipId,
                resourceType = p.resourceType,
                accessLevel = p.accessLevel,
                filterJson = p.filterJson,
                cascadeJson = p.cascadeJson,
                materialize = p.materialize,
                createdBy = p.createdBy,
                createdAt = p.createdAt,
                updatedAt = p.updatedAt,
                revokedAt = p.revokedAt,
            )
    }
}
