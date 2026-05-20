package com.synopticengine.api.sharing.repo

import com.synopticengine.api.sharing.domain.CrossTenantAudit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CrossTenantAuditRepository : JpaRepository<CrossTenantAudit, UUID> {
    fun findAllByOwnerTenantIdAndResourceTypeAndResourceIdOrderByAtDesc(
        ownerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
        pageable: Pageable,
    ): Page<CrossTenantAudit>

    fun findAllByActorTenantIdOrderByAtDesc(
        actorTenantId: UUID,
        pageable: Pageable,
    ): Page<CrossTenantAudit>

    fun findAllByOwnerTenantIdOrderByAtDesc(
        ownerTenantId: UUID,
        pageable: Pageable,
    ): Page<CrossTenantAudit>
}
