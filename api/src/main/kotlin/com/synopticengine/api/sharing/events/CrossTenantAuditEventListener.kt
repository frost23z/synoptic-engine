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
 * **Scope today: cross-tenant EDITs only.**
 * [CrossTenantAuditService.record] enforces `ownerTenantId != actorTenantId` — the
 * audit table exists specifically to track when a non-owner touches a record.
 *
 * - **EDIT**   — wired via [SharedRecordEditedEvent], which is published by
 *   [com.synopticengine.api.sharing.CrossTenantWriteListener] from JPA
 *   `@PostUpdate` on every shareable entity whose owner tenant differs from the
 *   current [com.synopticengine.api.shared.TenantContext]. Soft-deletes flow
 *   through here too because `@SQLDelete` is an UPDATE under the covers.
 *
 * The other actions defined in [CrossTenantAction] are not yet wired:
 *  - **SHARE / REVOKE** — published as [RecordSharedEvent] / [RecordShareRevokedEvent]
 *    today, but the actor is the owner tenant in both cases, so the
 *    same-tenant guard in [CrossTenantAuditService.record] would reject them.
 *    These actions live in `SharingEventLogger` as SLF4J entries until the
 *    events grow an explicit `actorTenantId`.
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
}
