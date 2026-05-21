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

    @Modifying
    @Query("DELETE FROM RecordShare r WHERE r.expiresAt IS NOT NULL AND r.expiresAt < :cutoff")
    fun deleteExpiredBefore(
        @Param("cutoff") cutoff: java.time.Instant,
    ): Int
}
