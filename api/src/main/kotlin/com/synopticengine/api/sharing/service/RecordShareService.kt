package com.synopticengine.api.sharing.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.RecordShare
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.domain.VisibilitySource
import com.synopticengine.api.sharing.repo.RecordShareRepository
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
 *  - Only users in the owner tenant may create shares for that tenant's records.
 *  - To reshare a record received via a share, the consumer needs MANAGE on it (per § 6.2).
 *  - Revoke can be performed by the owner tenant or the consumer (consumer can "opt out").
 */
@Service
class RecordShareService(
    private val recordShareRepository: RecordShareRepository,
    private val visibilityService: ResourceVisibilityService,
    private val tenantApi: TenantApi,
    private val crmApi: CrmApi,
) {
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

    @Transactional
    fun revoke(
        shareId: UUID,
        actingTenantId: UUID,
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
        return share
    }

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
        // The query returns owner-side rows (acting tenant is owner). Consumer-side
        // listing is handled by /api/records-shared-with-me later if needed; not in scope here.
        return rows.filter { it.revokedAt == null }
    }

    /**
     * Sanity check that the acting tenant is the owner of the record. Different rules per
     * resource type because each lives in its own module.
     */
    private fun verifyOwnership(
        ownerTenantId: UUID,
        resourceType: String,
        resourceId: UUID,
    ) {
        val actualOwner =
            when (resourceType) {
                ResourceType.LEADS.literal -> crmApi.findLeadOwnerTenant(resourceId)
                ResourceType.PERSONS.literal -> crmApi.findPersonOwnerTenant(resourceId)
                ResourceType.ORGANIZATIONS.literal -> crmApi.findOrganizationOwnerTenant(resourceId)
                // Other resource types: ownership check deferred; trust the caller.
                else -> ownerTenantId
            }
        if (actualOwner == null) {
            throw NoSuchElementException("$resourceType $resourceId not found")
        }
        if (actualOwner != ownerTenantId) {
            throw AccessDeniedException("Only the owner tenant may share this record")
        }
    }
}
