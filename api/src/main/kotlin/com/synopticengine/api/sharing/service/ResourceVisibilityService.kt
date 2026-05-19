package com.synopticengine.api.sharing.service

import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.ResourceVisibility
import com.synopticengine.api.sharing.domain.VisibilitySource
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Read-side façade over [ResourceVisibility]. Writes are batched through the
 * materialization worker (policy changes) or the record-share service (Sprint 2c).
 */
@Service
class ResourceVisibilityService(
    private val visibilityRepository: ResourceVisibilityRepository,
) {
    /**
     * The effective access level a consumer has on a single record across all visibility
     * sources. Returns [AccessLevel.NONE] when no visibility row applies (or all have expired).
     */
    @Transactional(readOnly = true)
    fun effectiveAccess(
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): AccessLevel {
        val rows =
            visibilityRepository
                .findAllByConsumerTenantIdAndResourceTypeAndResourceId(
                    consumerTenantId,
                    resourceType,
                    resourceId,
                ).filter { it.expiresAt == null || it.expiresAt!! > Instant.now() }
        if (rows.isEmpty()) return AccessLevel.NONE
        return rows.map { it.accessLevel }.reduce(AccessLevel::max)
    }

    /** All ids visible to [consumerTenantId] for [resourceType]. */
    @Transactional(readOnly = true)
    fun visibleIds(
        consumerTenantId: UUID,
        resourceType: String,
    ): Set<UUID> =
        visibilityRepository
            .findAllByConsumerTenantIdAndResourceType(consumerTenantId, resourceType)
            .filter { it.expiresAt == null || it.expiresAt!! > Instant.now() }
            .mapTo(mutableSetOf()) { it.resourceId }

    @Transactional
    fun upsert(
        ownerTenantId: UUID,
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
        accessLevel: AccessLevel,
        source: VisibilitySource,
        sourceId: UUID,
        expiresAt: Instant? = null,
    ): ResourceVisibility {
        val existing =
            visibilityRepository
                .findAllByConsumerTenantIdAndResourceTypeAndResourceId(
                    consumerTenantId,
                    resourceType,
                    resourceId,
                ).firstOrNull { it.source == source && it.sourceId == sourceId }
        if (existing != null) {
            existing.accessLevel = accessLevel
            existing.expiresAt = expiresAt
            return existing
        }
        val row =
            ResourceVisibility().apply {
                this.ownerTenantId = ownerTenantId
                this.consumerTenantId = consumerTenantId
                this.resourceType = resourceType
                this.resourceId = resourceId
                this.accessLevel = accessLevel
                this.source = source
                this.sourceId = sourceId
                this.expiresAt = expiresAt
            }
        return visibilityRepository.save(row)
    }

    @Transactional
    fun deleteBySource(
        source: VisibilitySource,
        sourceId: UUID,
    ): Int = visibilityRepository.deleteAllBySourceAndSourceId(source, sourceId)

    @Transactional
    fun deleteForOwnerResource(
        ownerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): Int = visibilityRepository.deleteAllByOwnerResource(ownerTenantId, resourceType, resourceId)
}
