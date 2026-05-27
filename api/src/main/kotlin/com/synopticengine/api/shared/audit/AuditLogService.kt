package com.synopticengine.api.shared.audit

import com.synopticengine.api.shared.ActorContext
import com.synopticengine.api.shared.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Append-only audit log for sensitive same-tenant mutations.
 *
 * Currently wired for:
 *  - `system_configs` CREATE / UPDATE / DELETE
 *  - `webhooks` CREATE / UPDATE / DELETE
 *
 * Usage pattern (mirror this in new callers):
 * ```kotlin
 * auditLogService.record(
 *     entityType  = "system_config",
 *     entityId    = config.code,
 *     action      = AuditAction.UPDATE,
 *     payload     = mapOf("code" to config.code, "isSecret" to config.isSecret),
 * )
 * ```
 *
 * The method uses `REQUIRES_NEW` propagation so an audit record is saved even
 * if the outer transaction is later rolled back. This trades audit accuracy
 * (a record exists even for a rolled-back mutation) for completeness (no silent
 * gaps). The trade-off is acceptable for compliance log use-cases.
 */
@Service
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
) {
    private val log = LoggerFactory.getLogger(AuditLogService::class.java)

    /**
     * Record an audit entry. `tenantId` defaults to [TenantContext.get()]; it
     * must be supplied explicitly for async / system paths where the context may
     * not be set.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        entityType: String,
        entityId: String?,
        action: AuditAction,
        payload: Map<String, Any?>? = null,
        tenantId: UUID? = TenantContext.get(),
        actorId: UUID? = ActorContext.get(),
    ) {
        val effectiveTenantId =
            tenantId
                ?: run {
                    log.warn(
                        "AuditLogService.record called without TenantContext for $entityType/$entityId — skipping",
                    )
                    return
                }
        auditLogRepository.save(
            AuditLog().apply {
                this.tenantId = effectiveTenantId
                this.actorId = actorId
                this.entityType = entityType
                this.entityId = entityId
                this.action = action
                this.payload = payload
                this.at = Instant.now()
            },
        )
    }
}
