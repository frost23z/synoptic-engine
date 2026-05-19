package com.synopticengine.api.sharing.repo

import com.synopticengine.api.sharing.domain.RecordShare
import org.springframework.data.jpa.repository.JpaRepository
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
}
