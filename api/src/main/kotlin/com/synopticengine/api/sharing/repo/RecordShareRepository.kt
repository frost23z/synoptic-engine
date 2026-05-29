package com.synopticengine.api.sharing.repo

import com.synopticengine.api.sharing.domain.RecordShare
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RecordShareRepository : JpaRepository<RecordShare, UUID> {
    fun findAllByOwnerTenantIdAndResourceTypeAndResourceId(
        ownerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): List<RecordShare>

    fun findByOwnerTenantIdAndConsumerTenantIdAndResourceTypeAndResourceId(
        ownerTenantId: UUID,
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): RecordShare?

    fun findAllByConsumerTenantId(consumerTenantId: UUID): List<RecordShare>

    /**
     * Returns the owner tenant for a shared resource by querying the cross-tenant
     * `record_shares` table (no RLS / Hibernate filter). Used during reshare to
     * find the owner without requiring the caller to be in the owner's tenant context.
     */
    @Query(
        "SELECT DISTINCT r.ownerTenantId FROM RecordShare r " +
            "WHERE r.resourceType = :resourceType AND r.resourceId = :resourceId AND r.revokedAt IS NULL",
    )
    fun findOwnerTenantByResource(
        @Param("resourceType") resourceType: String,
        @Param("resourceId") resourceId: UUID,
    ): UUID?

    @Modifying
    @Query("DELETE FROM RecordShare r WHERE r.expiresAt IS NOT NULL AND r.expiresAt < :cutoff")
    fun deleteExpiredBefore(
        @Param("cutoff") cutoff: java.time.Instant,
    ): Int
}
