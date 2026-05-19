package com.synopticengine.api.sharing

import com.synopticengine.api.shared.ActorContext
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.events.SharedRecordEditedEvent
import org.hibernate.Interceptor
import org.hibernate.type.Type
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * SessionFactory-scoped Hibernate interceptor that fires for every flush of a
 * dirty shareable entity. When the entity's `tenantId` differs from the current
 * [TenantContext], the write is cross-tenant and we publish a
 * [SharedRecordEditedEvent] — consumed by
 * [com.synopticengine.api.sharing.events.CrossTenantAuditEventListener] which
 * writes the durable audit row.
 *
 * Registered globally via [HibernatePropertiesCustomizer] (see the companion
 * `@Component` below). This is the Hibernate-Interceptor alternative to JPA
 * `@EntityListeners` — the same callback semantics, but with no compile-time
 * dependency from the entity (CRM / Inventory) back to this listener (sharing).
 * That preserves the Spring Modulith module-boundary contract:
 *
 *   crm   → sharing.SharingApi   (via CrmApiImpl reading SharingApi.effectiveAccess)
 *   sharing → crm.CrmApi          (RecordShareService cascade lookup)
 *
 * Adding a back-edge from `crm.lead.domain.Lead` to `sharing.CrossTenantWriteListener`
 * via `@EntityListeners` would have closed a `crm → sharing → crm` cycle —
 * something Modulith correctly rejects. The interceptor pattern keeps the
 * entity blissfully ignorant of who's watching it flush.
 *
 * Soft-deletes (the `@SQLDelete` UPDATE that sets `deleted_at = NOW()`) flow
 * through `onFlushDirty` just like any other update; an operator reading the
 * audit log sees the EDIT row and can check `deleted_at` on the record itself
 * to see that the modification was a logical delete.
 *
 * Out of scope (see [com.synopticengine.api.sharing.events.CrossTenantAuditEventListener]
 * for the rationale): VIEW, COMMENT, SHARE, RESHARE, REVOKE. Hard DELETE doesn't
 * happen on SoftDeletable entities.
 */
class CrossTenantWriteInterceptor(
    private val eventPublisher: ApplicationEventPublisher,
) : Interceptor {
    override fun onFlushDirty(
        entity: Any?,
        id: Any?,
        currentState: Array<out Any>?,
        previousState: Array<out Any>?,
        propertyNames: Array<out String>?,
        types: Array<out Type>?,
    ): Boolean {
        if (entity !is BaseEntity) return false
        val actorTenantId = TenantContext.get() ?: return false
        val actorUserId = ActorContext.get() ?: return false
        val ownerTenantId = entity.tenantId
        if (ownerTenantId == actorTenantId) return false
        val resourceType = resourceTypeFor(entity) ?: return false
        val resourceId = entity.id ?: return false

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
        // We didn't modify entity state; Hibernate must continue with its planned flush.
        return false
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

/**
 * Registers [CrossTenantWriteInterceptor] as the SessionFactory-level interceptor.
 * `hibernate.session_factory.interceptor` accepts an instance (vs.
 * `session_scoped_interceptor` which takes a class name and re-instantiates per
 * session) — we want the singleton so the injected [ApplicationEventPublisher]
 * is the live Spring publisher.
 */
@Suppress("unused")
@Component
class CrossTenantInterceptorCustomizer(
    private val eventPublisher: ApplicationEventPublisher,
) : HibernatePropertiesCustomizer {
    @Suppress("UNCHECKED_CAST")
    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties["hibernate.session_factory.interceptor"] =
            CrossTenantWriteInterceptor(eventPublisher)
    }
}

