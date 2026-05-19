package com.synopticengine.api.sharing.service

import com.synopticengine.api.identity.TenantApi
import com.synopticengine.api.sharing.domain.RelationshipStatus
import com.synopticengine.api.sharing.domain.RelationshipType
import com.synopticengine.api.sharing.domain.ShareMaterializationOp
import com.synopticengine.api.sharing.domain.TenantRelationship
import com.synopticengine.api.sharing.events.RelationshipAcceptedEvent
import com.synopticengine.api.sharing.events.RelationshipRequestedEvent
import com.synopticengine.api.sharing.events.RelationshipRevokedEvent
import com.synopticengine.api.sharing.repo.TenantRelationshipRepository
import com.synopticengine.api.sharing.repo.TenantSharePolicyRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Handshake lifecycle for [TenantRelationship]:
 *  - source admin calls [request]      → row created in PENDING
 *  - target admin calls [accept]       → status flips to ACTIVE
 *  - either side can call [suspend]    → ACTIVE → SUSPENDED, reversible
 *  - either side can call [revoke]     → terminal; visibility tears down in Sprint 2b+
 *
 * PARTNER relationships are modelled as two rows; this service does not auto-create
 * the inverse — the partner tenant must explicitly initiate or accept the return edge.
 */
@Service
class TenantRelationshipService(
    private val relationshipRepository: TenantRelationshipRepository,
    private val policyRepository: TenantSharePolicyRepository,
    private val materializationWorker: ShareMaterializationWorker,
    private val tenantApi: TenantApi,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun request(
        sourceTenantId: UUID,
        targetTenantId: UUID,
        relationshipType: RelationshipType,
        initiatedBy: UUID,
        note: String? = null,
    ): TenantRelationship {
        require(sourceTenantId != targetTenantId) { "Cannot relate a tenant to itself" }
        if (!tenantApi.exists(targetTenantId)) {
            throw NoSuchElementException("Target tenant not found")
        }
        val existing =
            relationshipRepository.findBySourceTenantIdAndTargetTenantIdAndRelationshipType(
                sourceTenantId,
                targetTenantId,
                relationshipType,
            )
        if (existing != null && existing.status != RelationshipStatus.REVOKED) {
            throw IllegalStateException("Relationship already exists with status ${existing.status}")
        }
        val relationship =
            TenantRelationship().apply {
                this.sourceTenantId = sourceTenantId
                this.targetTenantId = targetTenantId
                this.relationshipType = relationshipType
                this.initiatedBy = initiatedBy
                this.note = note
                this.status = RelationshipStatus.PENDING
            }
        val saved = relationshipRepository.save(relationship)
        eventPublisher.publishEvent(
            RelationshipRequestedEvent(
                relationshipId = saved.id!!,
                sourceTenantId = sourceTenantId,
                targetTenantId = targetTenantId,
                relationshipType = relationshipType,
                initiatedBy = initiatedBy,
            ),
        )
        return saved
    }

    @Transactional
    fun accept(
        relationshipId: UUID,
        actingTenantId: UUID,
        actingUserId: UUID,
    ): TenantRelationship {
        val rel = loadInvolved(relationshipId, actingTenantId)
        if (rel.targetTenantId != actingTenantId) {
            throw AccessDeniedException("Only the target tenant may accept this relationship")
        }
        if (rel.status != RelationshipStatus.PENDING) {
            throw IllegalStateException("Only PENDING relationships may be accepted")
        }
        rel.status = RelationshipStatus.ACTIVE
        rel.acceptedBy = actingUserId
        rel.acceptedAt = Instant.now()
        // Once the relationship is active, replay any pre-existing policies.
        policyRepository.findAllByRelationshipId(rel.id!!).forEach { policy ->
            materializationWorker.enqueue(policy.id!!, ShareMaterializationOp.INSERT)
        }
        eventPublisher.publishEvent(
            RelationshipAcceptedEvent(
                relationshipId = rel.id!!,
                sourceTenantId = rel.sourceTenantId,
                targetTenantId = rel.targetTenantId,
                acceptedBy = actingUserId,
            ),
        )
        return rel
    }

    @Transactional
    fun revoke(
        relationshipId: UUID,
        actingTenantId: UUID,
    ): TenantRelationship {
        val rel = loadInvolved(relationshipId, actingTenantId)
        // PARENT_CHILD: only the parent (source) may revoke at will. Child can suspend on their side
        // by revoking their reverse edge if it exists. For simplicity child cannot revoke parent's edge.
        if (rel.relationshipType == RelationshipType.PARENT_CHILD && rel.sourceTenantId != actingTenantId) {
            throw AccessDeniedException("Only the parent tenant may revoke a PARENT_CHILD relationship")
        }
        if (rel.status == RelationshipStatus.REVOKED) {
            return rel
        }
        rel.status = RelationshipStatus.REVOKED
        rel.revokedAt = Instant.now()
        // Tear down visibility for every policy on this relationship.
        policyRepository.findAllByRelationshipId(rel.id!!).forEach { policy ->
            materializationWorker.enqueue(policy.id!!, ShareMaterializationOp.REVOKE)
        }
        eventPublisher.publishEvent(
            RelationshipRevokedEvent(
                relationshipId = rel.id!!,
                sourceTenantId = rel.sourceTenantId,
                targetTenantId = rel.targetTenantId,
            ),
        )
        return rel
    }

    @Transactional
    fun suspend(
        relationshipId: UUID,
        actingTenantId: UUID,
    ): TenantRelationship {
        val rel = loadInvolved(relationshipId, actingTenantId)
        if (rel.status != RelationshipStatus.ACTIVE) {
            throw IllegalStateException("Only ACTIVE relationships may be suspended")
        }
        rel.status = RelationshipStatus.SUSPENDED
        // Suspended = visibility temporarily torn down; resuming re-materializes.
        policyRepository.findAllByRelationshipId(rel.id!!).forEach { policy ->
            materializationWorker.enqueue(policy.id!!, ShareMaterializationOp.REVOKE)
        }
        return rel
    }

    @Transactional
    fun resume(
        relationshipId: UUID,
        actingTenantId: UUID,
    ): TenantRelationship {
        val rel = loadInvolved(relationshipId, actingTenantId)
        if (rel.status != RelationshipStatus.SUSPENDED) {
            throw IllegalStateException("Only SUSPENDED relationships may be resumed")
        }
        rel.status = RelationshipStatus.ACTIVE
        policyRepository.findAllByRelationshipId(rel.id!!).forEach { policy ->
            materializationWorker.enqueue(policy.id!!, ShareMaterializationOp.INSERT)
        }
        return rel
    }

    @Transactional(readOnly = true)
    fun listFor(tenantId: UUID): List<TenantRelationship> =
        relationshipRepository.findAllBySourceTenantIdOrTargetTenantId(tenantId, tenantId)

    @Transactional(readOnly = true)
    fun get(
        relationshipId: UUID,
        actingTenantId: UUID,
    ): TenantRelationship = loadInvolved(relationshipId, actingTenantId)

    @Transactional(readOnly = true)
    fun listActiveOutbound(sourceTenantId: UUID): List<TenantRelationship> =
        relationshipRepository.findAllByStatusAndSourceTenantId(RelationshipStatus.ACTIVE, sourceTenantId)

    @Transactional(readOnly = true)
    fun listActiveInbound(targetTenantId: UUID): List<TenantRelationship> =
        relationshipRepository.findAllByStatusAndTargetTenantId(RelationshipStatus.ACTIVE, targetTenantId)

    private fun loadInvolved(
        relationshipId: UUID,
        actingTenantId: UUID,
    ): TenantRelationship {
        val rel =
            relationshipRepository
                .findById(relationshipId)
                .orElseThrow { NoSuchElementException("Relationship not found") }
        if (!rel.involves(actingTenantId)) {
            // Don't leak existence to non-participants — return 404, not 403.
            throw NoSuchElementException("Relationship not found")
        }
        return rel
    }
}
