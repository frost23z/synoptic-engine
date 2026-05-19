package com.synopticengine.api.sharing.events

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Bridges [SharingEvent]s to the application log. This is the minimum useful listener
 * for Sprint 2d — it confirms the event publication path works and produces a paper
 * trail in production logs while a full notifications table is wired up later.
 *
 * Other modules (notifications, mail, websocket push) can subscribe by adding their own
 * `@EventListener(SharingEvent::class)` methods; the publisher (TenantRelationshipService,
 * RecordShareService) doesn't need to know about them.
 */
@Component
class SharingEventLogger {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onRelationshipRequested(e: RelationshipRequestedEvent) {
        log.info(
            "RelationshipRequested rel={} source={} target={} type={} by={}",
            e.relationshipId,
            e.sourceTenantId,
            e.targetTenantId,
            e.relationshipType,
            e.initiatedBy,
        )
    }

    @EventListener
    fun onRelationshipAccepted(e: RelationshipAcceptedEvent) {
        log.info(
            "RelationshipAccepted rel={} source={} target={} acceptedBy={}",
            e.relationshipId,
            e.sourceTenantId,
            e.targetTenantId,
            e.acceptedBy,
        )
    }

    @EventListener
    fun onRelationshipRevoked(e: RelationshipRevokedEvent) {
        log.info(
            "RelationshipRevoked rel={} source={} target={}",
            e.relationshipId,
            e.sourceTenantId,
            e.targetTenantId,
        )
    }

    @EventListener
    fun onRecordShared(e: RecordSharedEvent) {
        log.info(
            "RecordShared share={} {}/{} owner={} consumer={} level={} by={}",
            e.shareId,
            e.resourceType,
            e.resourceId,
            e.ownerTenantId,
            e.consumerTenantId,
            e.accessLevel,
            e.sharedBy,
        )
    }

    @EventListener
    fun onRecordShareRevoked(e: RecordShareRevokedEvent) {
        log.info(
            "RecordShareRevoked share={} {}/{} owner={} consumer={}",
            e.shareId,
            e.resourceType,
            e.resourceId,
            e.ownerTenantId,
            e.consumerTenantId,
        )
    }

    @EventListener
    fun onSharedRecordEdited(e: SharedRecordEditedEvent) {
        log.info(
            "SharedRecordEdited owner={} actorTenant={} actorUser={} {}/{}",
            e.ownerTenantId,
            e.actorTenantId,
            e.actorUserId,
            e.resourceType,
            e.resourceId,
        )
    }
}
