package com.synopticengine.api.sharing.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.inventory.InventoryApi
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.RecordShare
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.domain.VisibilitySource
import com.synopticengine.api.sharing.events.RecordShareRevokedEvent
import com.synopticengine.api.sharing.events.RecordSharedEvent
import com.synopticengine.api.sharing.repo.RecordShareRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Per-record share lifecycle. Each share is a row in `record_shares` plus a
 * `resource_visibility` row (source = RECORD) plus, by default cascade rules,
 * sub-resource visibility rows (source = CASCADE).
 *
 * Authorisation:
 *  - Only users in the owner tenant may create shares for that tenant's records
 *    (via [share]).
 *  - A consumer with MANAGE access may reshare onward, gated on
 *    [com.synopticengine.api.sharing.SharingPermissions.RECORDS_RESHARE] + [reshare].
 *  - Revoke can be performed by the owner tenant or the consumer (consumer "opts out").
 *
 * Defense-in-depth:
 *  - [assertCanWrite] / [assertCanDelete] enforce access-level checks at the
 *    service layer in addition to Postgres RLS, for cross-tenant mutation paths.
 */
@Service
class RecordShareService(
    private val recordShareRepository: RecordShareRepository,
    private val visibilityService: ResourceVisibilityService,
    private val tenantApi: TenantApi,
    private val crmApi: CrmApi,
    private val inventoryApi: InventoryApi,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // ── Owner-side share ──────────────────────────────────────────────────────

    @Transactional
    fun share(
        ownerTenantId: UUID,
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
        accessLevel: AccessLevel,
        sharedBy: UUID,
        expiresAt: Instant? = null,
        note: String? = null,
    ): RecordShare {
        require(ownerTenantId != consumerTenantId) { "Cannot share a record with the owner tenant itself" }
        if (!tenantApi.exists(consumerTenantId)) {
            throw NoSuchElementException("Consumer tenant not found")
        }
        require(ResourceType.isKnown(resourceType)) { "Unknown resource type: $resourceType" }

        verifyOwnership(ownerTenantId, resourceType, resourceId)

        return doShare(
            ownerTenantId,
            consumerTenantId,
            resourceType,
            resourceId,
            accessLevel,
            sharedBy,
            expiresAt,
            note,
        )
    }

    // ── Consumer-initiated reshare (MANAGE access → share onward) ─────────────

    /**
     * Reshare a record received via a share. The acting tenant must have
     * [AccessLevel.MANAGE] on the resource (checked via [ResourceVisibilityService.effectiveAccess]).
     *
     * The reshared access level is **capped** at the actor's own effective level;
     * a consumer cannot grant more than they have.
     *
     * Gate the endpoint on [com.synopticengine.api.sharing.SharingPermissions.RECORDS_RESHARE]
     * before calling this method.
     */
    @Transactional
    fun reshare(
        actingTenantId: UUID,
        actingUserId: UUID,
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
        accessLevel: AccessLevel,
        expiresAt: Instant? = null,
        note: String? = null,
    ): RecordShare {
        require(actingTenantId != consumerTenantId) { "Cannot reshare with your own tenant" }
        if (!tenantApi.exists(consumerTenantId)) {
            throw NoSuchElementException("Consumer tenant not found")
        }
        require(ResourceType.isKnown(resourceType)) { "Unknown resource type: $resourceType" }

        // Check that the acting tenant has MANAGE (canReshare) access.
        val effectiveLevel = visibilityService.effectiveAccess(actingTenantId, resourceType, resourceId)
        if (!effectiveLevel.canReshare()) {
            throw AccessDeniedException(
                "Tenant $actingTenantId does not have MANAGE access on $resourceType $resourceId " +
                    "(effective: $effectiveLevel) — MANAGE is required to reshare",
            )
        }

        val ownerTenantId =
            findOwnerTenant(resourceType, resourceId)
                ?: throw NoSuchElementException("$resourceType $resourceId not found")

        require(ownerTenantId != consumerTenantId) { "Cannot reshare back to the owner tenant" }

        // Cap: the resharer cannot grant more than their own access level.
        val cappedLevel = if (accessLevel.ordinal <= effectiveLevel.ordinal) accessLevel else effectiveLevel

        return doShare(
            ownerTenantId,
            consumerTenantId,
            resourceType,
            resourceId,
            cappedLevel,
            actingUserId,
            expiresAt,
            note,
        )
    }

    // ── Defense-in-depth guards (call from cross-tenant mutation paths) ────────

    /**
     * Asserts that [consumerTenantId] has at least WRITE access on [resourceType/resourceId].
     *
     * Call this from service methods that modify a shared resource on behalf of a
     * consumer tenant — in addition to Postgres RLS — as defense-in-depth.
     *
     * @throws AccessDeniedException when the effective access level is insufficient.
     */
    fun assertCanWrite(
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ) {
        val access = visibilityService.effectiveAccess(consumerTenantId, resourceType, resourceId)
        if (!access.canWrite()) {
            throw AccessDeniedException(
                "Tenant $consumerTenantId does not have WRITE access on $resourceType $resourceId (effective: $access)",
            )
        }
    }

    /**
     * Asserts that [consumerTenantId] has MANAGE (canDelete) access on [resourceType/resourceId].
     *
     * Call from service methods that delete a shared resource on behalf of a consumer.
     *
     * @throws AccessDeniedException when the effective access level is insufficient.
     */
    fun assertCanDelete(
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ) {
        val access = visibilityService.effectiveAccess(consumerTenantId, resourceType, resourceId)
        if (!access.canDelete()) {
            throw AccessDeniedException(
                "Tenant $consumerTenantId does not have MANAGE access on $resourceType $resourceId (effective: $access)",
            )
        }
    }

    // ── Revoke ────────────────────────────────────────────────────────────────

    @Transactional
    fun revoke(
        shareId: UUID,
        actingTenantId: UUID,
        actingUserId: UUID,
    ): RecordShare {
        val share =
            recordShareRepository
                .findById(shareId)
                .orElseThrow { NoSuchElementException("Share not found") }
        if (share.ownerTenantId != actingTenantId && share.consumerTenantId != actingTenantId) {
            throw NoSuchElementException("Share not found")
        }
        if (share.revokedAt != null) return share
        share.revokedAt = Instant.now()
        // Remove direct + cascaded visibility rows attached to this share.
        visibilityService.deleteBySource(VisibilitySource.RECORD, share.id!!)
        visibilityService.deleteBySource(VisibilitySource.CASCADE, share.id!!)
        eventPublisher.publishEvent(
            RecordShareRevokedEvent(
                shareId = share.id!!,
                ownerTenantId = share.ownerTenantId,
                consumerTenantId = share.consumerTenantId,
                revokedByTenantId = actingTenantId,
                revokedBy = actingUserId,
                resourceType = share.resourceType,
                resourceId = share.resourceId,
            ),
        )
        return share
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listShares(
        actingTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ): List<RecordShare> {
        val rows =
            recordShareRepository.findAllByOwnerTenantIdAndResourceTypeAndResourceId(
                actingTenantId,
                resourceType,
                resourceId,
            )
        return rows.filter { it.revokedAt == null }
    }

    @Transactional(readOnly = true)
    fun listSharedWithMe(
        actingTenantId: UUID,
        resourceType: String?,
    ): List<RecordShare> {
        val now = Instant.now()
        return recordShareRepository
            .findAllByConsumerTenantId(actingTenantId)
            .asSequence()
            .filter { resourceType == null || it.resourceType == resourceType }
            .filter { it.revokedAt == null && (it.expiresAt == null || it.expiresAt!!.isAfter(now)) }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Core upsert logic shared by [share] and [reshare].
     * Callers are responsible for any ownership/access checks.
     */
    private fun doShare(
        ownerTenantId: UUID,
        consumerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
        accessLevel: AccessLevel,
        sharedBy: UUID,
        expiresAt: Instant?,
        note: String?,
    ): RecordShare {
        // Upsert: if a non-revoked share already exists, update access level instead of duplicating.
        val existing =
            recordShareRepository.findByOwnerTenantIdAndConsumerTenantIdAndResourceTypeAndResourceId(
                ownerTenantId = ownerTenantId,
                consumerTenantId = consumerTenantId,
                resourceType = resourceType,
                resourceId = resourceId,
            )
        val share =
            if (existing != null && existing.revokedAt == null) {
                existing.accessLevel = accessLevel
                existing.expiresAt = expiresAt
                existing.note = note ?: existing.note
                existing
            } else {
                recordShareRepository.save(
                    RecordShare().apply {
                        this.ownerTenantId = ownerTenantId
                        this.consumerTenantId = consumerTenantId
                        this.resourceType = resourceType
                        this.resourceId = resourceId
                        this.accessLevel = accessLevel
                        this.sharedBy = sharedBy
                        this.expiresAt = expiresAt
                        this.note = note
                    },
                )
            }

        // Direct visibility for the shared record.
        visibilityService.upsert(
            ownerTenantId = ownerTenantId,
            consumerTenantId = consumerTenantId,
            resourceType = resourceType,
            resourceId = resourceId,
            accessLevel = accessLevel,
            source = VisibilitySource.RECORD,
            sourceId = share.id!!,
            expiresAt = expiresAt,
        )

        eventPublisher.publishEvent(
            RecordSharedEvent(
                shareId = share.id!!,
                ownerTenantId = ownerTenantId,
                consumerTenantId = consumerTenantId,
                resourceType = resourceType,
                resourceId = resourceId,
                accessLevel = accessLevel,
                sharedBy = sharedBy,
            ),
        )

        // Cascade — for `leads`, propagate to person/org as READ.
        if (resourceType == ResourceType.LEADS.literal) {
            val info = crmApi.findLeadCascadeInfo(resourceId)
            if (info != null) {
                CascadeRules
                    .defaultCascadeFor(
                        parentType = ResourceType.LEADS,
                        personId = info.personId,
                        organizationId = info.organizationId,
                    ).forEach { target ->
                        visibilityService.upsert(
                            ownerTenantId = ownerTenantId,
                            consumerTenantId = consumerTenantId,
                            resourceType = target.resourceType.literal,
                            resourceId = target.resourceId,
                            accessLevel = target.accessLevel,
                            source = VisibilitySource.CASCADE,
                            sourceId = share.id!!,
                            expiresAt = expiresAt,
                        )
                    }
            }
        }

        return share
    }

    /**
     * Sanity check that the acting tenant is the owner of the record. Each resource type
     * lives in its own module so the lookup goes through the module port — never trust the
     * caller-supplied owner: a tenant could otherwise create a share for a record owned by
     * a different tenant it can see via cross-tenant visibility.
     */
    private fun verifyOwnership(
        ownerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ) {
        val actualOwner =
            findOwnerTenant(resourceType, resourceId)
                ?: throw NoSuchElementException("$resourceType $resourceId not found")
        if (actualOwner != ownerTenantId) {
            throw AccessDeniedException("Only the owner tenant may share this record")
        }
    }

    private fun findOwnerTenant(
        resourceType: String,
        resourceId: UUID,
    ): UUID? {
        val rt =
            try {
                ResourceType.fromLiteral(resourceType)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown resource type: $resourceType")
            }
        return when (rt) {
            ResourceType.LEADS -> crmApi.findLeadOwnerTenant(resourceId)
            ResourceType.PERSONS -> crmApi.findPersonOwnerTenant(resourceId)
            ResourceType.ORGANIZATIONS -> crmApi.findOrganizationOwnerTenant(resourceId)
            ResourceType.QUOTES -> crmApi.findQuoteOwnerTenant(resourceId)
            ResourceType.ACTIVITIES -> crmApi.findActivityOwnerTenant(resourceId)
            ResourceType.PRODUCTS -> inventoryApi.findProductOwnerTenant(resourceId)
            ResourceType.WAREHOUSES -> inventoryApi.findWarehouseOwnerTenant(resourceId)
        }
    }
}
