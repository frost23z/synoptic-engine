package com.synopticengine.api.sharing.service

import com.synopticengine.api.sharing.SharingApi
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.CrossTenantAction
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class SharingApiImpl(
    private val visibilityService: ResourceVisibilityService,
    private val auditService: CrossTenantAuditService,
) : SharingApi {
    override fun effectiveAccess(
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): AccessLevel = visibilityService.effectiveAccess(consumerTenantId, resourceType, resourceId)

    override fun recordAudit(
        ownerTenantId: UUID,
        actorTenantId: UUID,
        actorUserId: UUID,
        resourceType: String,
        resourceId: UUID,
        action: CrossTenantAction,
        payloadJson: String?,
    ) {
        auditService.record(
            ownerTenantId = ownerTenantId,
            actorTenantId = actorTenantId,
            actorUserId = actorUserId,
            resourceType = resourceType,
            resourceId = resourceId,
            action = action,
            payloadJson = payloadJson,
        )
    }
}
