package com.synopticengine.api.sharing

import com.synopticengine.api.shared.ActorContext
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.events.SharedRecordEditedEvent
import jakarta.persistence.PostUpdate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * JPA entity listener that fires for every UPDATE on a shareable entity (Lead,
 * Person, Organization, Product). When the acting tenant differs from the entity's
 * owner tenant, the update is a cross-tenant write and we publish a
 * [SharedRecordEditedEvent] so [com.synopticengine.api.sharing.events.CrossTenantAuditEventListener]
 * writes the audit row.
 *
 * Soft-deletes (setting `deletedAt` via `@SQLDelete`) flow through here too — JPA
 * sees them as updates. That's intentional: the audit row records that a
 * cross-tenant actor modified the record; the operator can read `deleted_at` on the
 * record itself to see whether that modification was a logical delete.
 *
 * No event is published when:
 *  - `TenantContext` is unset (system code paths — bootstrap, scheduled jobs without
 *    a per-task context); these are not cross-tenant by definition.
 *  - `ActorContext` is unset (no authenticated actor — same reasoning).
 *  - The acting tenant equals the entity's owner tenant (in-tenant edit; not audited
 *    here).
 *
 * Registered via `@EntityListeners(CrossTenantWriteListener::class)` on
 * `Lead`, `Person`, `Organization`, `Product`. Spring's `SpringBeanContainer` (the
 * default in Spring Boot 4 / Hibernate 7) hands JPA the singleton bean instance so
 * `eventPublisher` is wired by constructor injection — same mechanism that lets
 * `AuditingEntityListener` reach `AuditorAware`.
 */
@Component
class CrossTenantWriteListener(
    private val eventPublisher: ApplicationEventPublisher,
) {
    @PostUpdate
    fun onPostUpdate(entity: Any) {
        if (entity !is BaseEntity) return
        val actorTenantId = TenantContext.get() ?: return
        val actorUserId = ActorContext.get() ?: return
        val ownerTenantId = entity.tenantId
        if (ownerTenantId == actorTenantId) return
        val resourceType = resourceTypeFor(entity) ?: return
        val resourceId = entity.id ?: return

        eventPublisher.publishEvent(
            SharedRecordEditedEvent(
                ownerTenantId = ownerTenantId,
                actorTenantId = actorTenantId,
                actorUserId = actorUserId,
                resourceType = resourceType.literal,
                resourceId = resourceId,
                fieldDiffJson = null,
            ),
        )
    }

    private fun resourceTypeFor(entity: BaseEntity): ResourceType? =
        when (entity::class.simpleName) {
            "Lead" -> ResourceType.LEADS
            "Person" -> ResourceType.PERSONS
            "Organization" -> ResourceType.ORGANIZATIONS
            "Product" -> ResourceType.PRODUCTS
            else -> null
        }
}
