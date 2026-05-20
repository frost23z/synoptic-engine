package com.synopticengine.api.sharing.service

import com.synopticengine.api.sharing.domain.CrossTenantAction
import com.synopticengine.api.sharing.domain.CrossTenantAudit
import com.synopticengine.api.sharing.repo.CrossTenantAuditRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Append-only audit log of cross-tenant actions. Other modules call [record] from
 * service methods that mutate a record on behalf of a non-owner consumer.
 */
@Service
class CrossTenantAuditService(
    private val auditRepository: CrossTenantAuditRepository,
) {
    @Transactional
    fun record(
        ownerTenantId: UUID,
        actorTenantId: UUID,
        actorUserId: UUID,
        resourceType: String,
        resourceId: UUID,
        action: CrossTenantAction,
        payloadJson: String? = null,
    ): CrossTenantAudit {
        // Guard: only log when the actor crosses the tenant boundary. Same-tenant
        // actions are covered by Spring Modulith's regular audit.
        require(ownerTenantId != actorTenantId) {
            "CrossTenantAuditService.record() called for same-tenant action: $action on $resourceType/$resourceId"
        }
        val row =
            CrossTenantAudit().apply {
                this.ownerTenantId = ownerTenantId
                this.actorTenantId = actorTenantId
                this.actorUserId = actorUserId
                this.resourceType = resourceType
                this.resourceId = resourceId
                this.action = action
                this.payloadJson = payloadJson
            }
        return auditRepository.save(row)
    }

    @Transactional(readOnly = true)
    fun byOwnerResource(
        ownerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
        pageable: Pageable,
    ): Page<CrossTenantAudit> =
        auditRepository.findAllByOwnerTenantIdAndResourceTypeAndResourceIdOrderByAtDesc(
            ownerTenantId,
            resourceType,
            resourceId,
            pageable,
        )

    @Transactional(readOnly = true)
    fun byActor(
        actorTenantId: UUID,
        pageable: Pageable,
    ): Page<CrossTenantAudit> = auditRepository.findAllByActorTenantIdOrderByAtDesc(actorTenantId, pageable)

    /** Every action other tenants have taken against this tenant's records, across all resources. */
    @Transactional(readOnly = true)
    fun byOwner(
        ownerTenantId: UUID,
        pageable: Pageable,
    ): Page<CrossTenantAudit> = auditRepository.findAllByOwnerTenantIdOrderByAtDesc(ownerTenantId, pageable)
}
