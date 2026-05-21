package com.synopticengine.api.sharing.repo

import com.synopticengine.api.sharing.domain.ResourceVisibility
import com.synopticengine.api.sharing.domain.VisibilitySource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ResourceVisibilityRepository : JpaRepository<ResourceVisibility, UUID> {
    fun findAllByConsumerTenantIdAndResourceType(
        consumerTenantId: UUID,
        resourceType: String,
    ): List<ResourceVisibility>

    fun findAllByConsumerTenantIdAndResourceTypeAndResourceId(
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): List<ResourceVisibility>

    fun findAllBySourceAndSourceId(
        source: VisibilitySource,
        sourceId: UUID,
    ): List<ResourceVisibility>

    @Modifying
    @Query("DELETE FROM ResourceVisibility v WHERE v.source = :source AND v.sourceId = :sourceId")
    fun deleteAllBySourceAndSourceId(
        @Param("source") source: VisibilitySource,
        @Param("sourceId") sourceId: UUID,
    ): Int

    @Modifying
    @Query(
        "DELETE FROM ResourceVisibility v WHERE v.ownerTenantId = :ownerTenantId AND v.resourceType = :resourceType AND v.resourceId = :resourceId",
    )
    fun deleteAllByOwnerResource(
        @Param("ownerTenantId") ownerTenantId: UUID,
        @Param("resourceType") resourceType: String,
        @Param("resourceId") resourceId: UUID,
    ): Int

    @Modifying
    @Query("DELETE FROM ResourceVisibility v WHERE v.expiresAt IS NOT NULL AND v.expiresAt < :cutoff")
    fun deleteExpiredBefore(
        @Param("cutoff") cutoff: java.time.Instant,
    ): Int
}
