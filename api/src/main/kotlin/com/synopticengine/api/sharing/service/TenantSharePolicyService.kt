package com.synopticengine.api.sharing.service

import com.synopticengine.api.sharing.domain.AccessLevel
import com.synopticengine.api.sharing.domain.RelationshipStatus
import com.synopticengine.api.sharing.domain.ResourceType
import com.synopticengine.api.sharing.domain.ShareMaterializationOp
import com.synopticengine.api.sharing.domain.TenantSharePolicy
import com.synopticengine.api.sharing.repo.TenantRelationshipRepository
import com.synopticengine.api.sharing.repo.TenantSharePolicyRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * CRUD for [TenantSharePolicy]. The owning tenant of a policy is implicitly the
 * `source_tenant_id` of the relationship — only they may create/edit/revoke.
 *
 * Sprint 2a: data only. Sprint 2b adds materialization into `resource_visibility`
 * via the materialization queue + worker.
 */
@Service
class TenantSharePolicyService(
    private val policyRepository: TenantSharePolicyRepository,
    private val relationshipRepository: TenantRelationshipRepository,
    private val materializationWorker: ShareMaterializationWorker,
) {
    @Transactional
    fun create(
        relationshipId: UUID,
        actingTenantId: UUID,
        actingUserId: UUID,
        resourceType: String,
        accessLevel: AccessLevel,
        filterJson: String? = null,
        cascadeJson: String? = null,
        materialize: Boolean = true,
    ): TenantSharePolicy {
        val rel = loadOwnedRelationship(relationshipId, actingTenantId)
        if (rel.status == RelationshipStatus.REVOKED) {
            throw IllegalStateException("Cannot create policies on a revoked relationship")
        }
        require(ResourceType.isKnown(resourceType)) { "Unknown resource type: $resourceType" }
        val existing = policyRepository.findByRelationshipIdAndResourceType(relationshipId, resourceType)
        if (existing != null) {
            throw IllegalStateException("A policy for $resourceType already exists on this relationship")
        }
        val policy =
            TenantSharePolicy().apply {
                this.relationshipId = relationshipId
                this.resourceType = resourceType
                this.accessLevel = accessLevel
                this.filterJson = filterJson
                this.cascadeJson = cascadeJson
                this.materialize = materialize
                this.createdBy = actingUserId
            }
        val saved = policyRepository.save(policy)
        materializationWorker.enqueue(saved.id!!, ShareMaterializationOp.INSERT)
        return saved
    }

    @Transactional
    fun update(
        policyId: UUID,
        actingTenantId: UUID,
        accessLevel: AccessLevel? = null,
        filterJson: String? = null,
        cascadeJson: String? = null,
    ): TenantSharePolicy {
        val policy = loadOwnedPolicy(policyId, actingTenantId)
        var changed = false
        if (accessLevel != null && policy.accessLevel != accessLevel) {
            policy.accessLevel = accessLevel
            changed = true
        }
        if (filterJson != null && policy.filterJson != filterJson) {
            policy.filterJson = filterJson
            changed = true
        }
        if (cascadeJson != null && policy.cascadeJson != cascadeJson) {
            policy.cascadeJson = cascadeJson
            changed = true
        }
        if (changed) materializationWorker.enqueue(policy.id!!, ShareMaterializationOp.UPDATE)
        return policy
    }

    @Transactional
    fun revoke(
        policyId: UUID,
        actingTenantId: UUID,
    ): TenantSharePolicy {
        val policy = loadOwnedPolicy(policyId, actingTenantId)
        policy.revokedAt = Instant.now()
        materializationWorker.enqueue(policy.id!!, ShareMaterializationOp.REVOKE)
        return policy
    }

    @Transactional(readOnly = true)
    fun listForRelationship(
        relationshipId: UUID,
        actingTenantId: UUID,
    ): List<TenantSharePolicy> {
        // Both ends may view policies governing them.
        val rel =
            relationshipRepository
                .findById(relationshipId)
                .orElseThrow { NoSuchElementException("Relationship not found") }
        if (!rel.involves(actingTenantId)) {
            throw NoSuchElementException("Relationship not found")
        }
        return policyRepository.findAllByRelationshipId(relationshipId)
    }

    @Transactional(readOnly = true)
    fun get(
        policyId: UUID,
        actingTenantId: UUID,
    ): TenantSharePolicy {
        val policy =
            policyRepository
                .findById(policyId)
                .orElseThrow { NoSuchElementException("Policy not found") }
        val rel =
            relationshipRepository
                .findById(policy.relationshipId)
                .orElseThrow { NoSuchElementException("Policy not found") }
        if (!rel.involves(actingTenantId)) {
            throw NoSuchElementException("Policy not found")
        }
        return policy
    }

    private fun loadOwnedRelationship(
        relationshipId: UUID,
        actingTenantId: UUID,
    ) = relationshipRepository.findById(relationshipId).orElseThrow {
        NoSuchElementException("Relationship not found")
    }.also {
        if (it.sourceTenantId != actingTenantId) {
            // Mutation requires being the source tenant.
            throw AccessDeniedException("Only the source tenant of a relationship may manage its policies")
        }
    }

    private fun loadOwnedPolicy(
        policyId: UUID,
        actingTenantId: UUID,
    ): TenantSharePolicy {
        val policy =
            policyRepository
                .findById(policyId)
                .orElseThrow { NoSuchElementException("Policy not found") }
        loadOwnedRelationship(policy.relationshipId, actingTenantId)
        return policy
    }
}
