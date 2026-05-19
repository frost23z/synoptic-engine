package com.synopticengine.api.sharing.repo

import com.synopticengine.api.sharing.domain.TenantSharePolicy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantSharePolicyRepository : JpaRepository<TenantSharePolicy, UUID> {
    fun findAllByRelationshipId(relationshipId: UUID): List<TenantSharePolicy>

    fun findByRelationshipIdAndResourceType(
        relationshipId: UUID,
        resourceType: String,
    ): TenantSharePolicy?

    fun findAllByResourceType(resourceType: String): List<TenantSharePolicy>
}
