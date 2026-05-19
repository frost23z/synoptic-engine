package com.synopticengine.api.sharing.events

import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.RelationshipType
import java.time.Instant
import java.util.UUID

/**
 * Spring [org.springframework.context.ApplicationEvent]s emitted by the sharing module.
 * Listeners (other modules, the notifications writer in this module) react and turn
 * these into user-visible notifications, audit entries, or UI badges.
 *
 * Keep these as data classes — Spring Modulith verifies that downstream modules only
 * see the API package; events live alongside `SharingApi`.
 */
sealed interface SharingEvent {
    val occurredAt: Instant
}

data class RelationshipRequestedEvent(
    val relationshipId: UUID,
    val sourceTenantId: UUID,
    val targetTenantId: UUID,
    val relationshipType: RelationshipType,
    val initiatedBy: UUID,
    override val occurredAt: Instant = Instant.now(),
) : SharingEvent

data class RelationshipAcceptedEvent(
    val relationshipId: UUID,
    val sourceTenantId: UUID,
    val targetTenantId: UUID,
    val acceptedBy: UUID,
    override val occurredAt: Instant = Instant.now(),
) : SharingEvent

data class RelationshipRevokedEvent(
    val relationshipId: UUID,
    val sourceTenantId: UUID,
    val targetTenantId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : SharingEvent

data class RecordSharedEvent(
    val shareId: UUID,
    val ownerTenantId: UUID,
    val consumerTenantId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val accessLevel: AccessLevel,
    val sharedBy: UUID,
    override val occurredAt: Instant = Instant.now(),
) : SharingEvent

data class RecordShareRevokedEvent(
    val shareId: UUID,
    val ownerTenantId: UUID,
    val consumerTenantId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : SharingEvent

data class SharedRecordEditedEvent(
    val ownerTenantId: UUID,
    val actorTenantId: UUID,
    val actorUserId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val fieldDiffJson: String?,
    override val occurredAt: Instant = Instant.now(),
) : SharingEvent
