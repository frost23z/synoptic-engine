package com.synopticengine.api.shared.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findAllByTenantIdAndEntityTypeAndEntityIdOrderByAtDesc(
        tenantId: UUID,
        entityType: String,
        entityId: String,
        pageable: Pageable,
    ): Page<AuditLog>

    fun findAllByTenantIdOrderByAtDesc(
        tenantId: UUID,
        pageable: Pageable,
    ): Page<AuditLog>
}
