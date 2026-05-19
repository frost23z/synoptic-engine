package com.synopticengine.api.sharing.repo

import com.synopticengine.api.sharing.domain.RelationshipStatus
import com.synopticengine.api.sharing.domain.RelationshipType
import com.synopticengine.api.sharing.domain.TenantRelationship
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantRelationshipRepository : JpaRepository<TenantRelationship, UUID> {
    fun findAllBySourceTenantIdOrTargetTenantId(
        sourceTenantId: UUID,
        targetTenantId: UUID,
    ): List<TenantRelationship>

    fun findAllBySourceTenantId(sourceTenantId: UUID): List<TenantRelationship>

    fun findAllByTargetTenantId(targetTenantId: UUID): List<TenantRelationship>

    fun findBySourceTenantIdAndTargetTenantIdAndRelationshipType(
        sourceTenantId: UUID,
        targetTenantId: UUID,
        relationshipType: RelationshipType,
    ): TenantRelationship?

    fun findAllByStatusAndSourceTenantId(
        status: RelationshipStatus,
        sourceTenantId: UUID,
    ): List<TenantRelationship>

    fun findAllByStatusAndTargetTenantId(
        status: RelationshipStatus,
        targetTenantId: UUID,
    ): List<TenantRelationship>
}
