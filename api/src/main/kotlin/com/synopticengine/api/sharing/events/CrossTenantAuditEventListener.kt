package com.synopticengine.api.sharing.events

import com.synopticengine.api.sharing.domain.CrossTenantAction
import com.synopticengine.api.sharing.service.CrossTenantAuditService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Persists [SharingEvent]s as [com.synopticengine.api.sharing.domain.CrossTenantAudit]
 * rows. Sibling of [SharingEventLogger]; the logger writes to SLF4J, this writer
 * lands the durable audit trail.
 *
 * Actions recorded today:
 *  - **EDIT**   — published by [com.synopticengine.api.sharing.CrossTenantWriteListener]
 *    from JPA `@PostUpdate` on every shareable entity whose owner tenant differs
 *    from the current [com.synopticengine.api.shared.TenantContext]. Soft-deletes
 *    flow through here too because `@SQLDelete` is an UPDATE under the covers.
 *  - **SHARE / REVOKE** — owner-initiated actions. The actor IS the owner tenant
 *    in both cases; [CrossTenantAuditService.record] relaxes its same-tenant
 *    guard for these so the owner's compliance review can see every grant they
 *    made and every revocation that happened against the consumer.
 *
 * Not yet wired:
 *  - **RESHARE** — when a consumer shares a record they received with a third
 *    party. RecordShareService doesn't yet distinguish the consumer-resharing
 *    path from the owner-sharing path; a follow-up should split them.
 *  - **VIEW** — too noisy without product requirements; deferred.
 *  - **COMMENT** — no comment subsystem exists yet.
 *
 * We use a synchronous `@EventListener` (default) so the audit row commits in
 * the same transaction as the mutation it records. Losing the audit row is a
 * worse failure than the action itself rolling back due to an audit failure —
 * the audit table is the durable record that someone touched a shared resource.
 */
@Component
class CrossTenantAuditEventListener(
    private val auditService: CrossTenantAuditService,
) {
    @EventListener
    fun onSharedRecordEdited(e: SharedRecordEditedEvent) {
        // CrossTenantWriteListener already enforces actor.tenant != owner.tenant
        // before publishing; the auditService.record guard is a defense in depth.
        auditService.record(
            ownerTenantId = e.ownerTenantId,
            actorTenantId = e.actorTenantId,
            actorUserId = e.actorUserId,
            resourceType = e.resourceType,
            resourceId = e.resourceId,
            action = CrossTenantAction.EDIT,
            payloadJson = e.fieldDiffJson,
        )
    }

    @EventListener
    fun onRecordShared(e: RecordSharedEvent) {
        // Owner-side action: actorTenantId == ownerTenantId. The same-tenant
        // guard in record() is relaxed for SHARE/REVOKE.
        auditService.record(
            ownerTenantId = e.ownerTenantId,
            actorTenantId = e.ownerTenantId,
            actorUserId = e.sharedBy,
            resourceType = e.resourceType,
            resourceId = e.resourceId,
            action = CrossTenantAction.SHARE,
            payloadJson =
                """{"shareId":"${e.shareId}","consumerTenantId":"${e.consumerTenantId}","accessLevel":"${e.accessLevel}"}""",
        )
    }

    @EventListener
    fun onRecordShareRevoked(e: RecordShareRevokedEvent) {
        auditService.record(
            ownerTenantId = e.ownerTenantId,
            actorTenantId = e.revokedByTenantId,
            actorUserId = e.revokedBy,
            resourceType = e.resourceType,
            resourceId = e.resourceId,
            action = CrossTenantAction.REVOKE,
            payloadJson = """{"shareId":"${e.shareId}","consumerTenantId":"${e.consumerTenantId}"}""",
        )
    }
}
