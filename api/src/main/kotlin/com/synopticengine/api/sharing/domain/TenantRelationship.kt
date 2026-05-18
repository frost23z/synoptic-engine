package com.synopticengine.api.sharing.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Directed edge between two tenants. The relationship lives at the platform layer (no
 * `tenant_id` column — it spans tenants by definition), but each row is administered
 * by `source_tenant_id`.
 *
 * Sprint 2a covers handshake-only (PENDING → ACTIVE → REVOKED); Sprint 2b layers
 * `tenant_share_policies` on top and Sprint 2c adds `record_shares`.
 */
@Entity
@Table(
    name = "tenant_relationships",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_tenant_relationship",
            columnNames = ["source_tenant_id", "target_tenant_id", "relationship_type"],
        ),
    ],
)
@EntityListeners(AuditingEntityListener::class)
class TenantRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(name = "source_tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    var sourceTenantId: UUID = UUID(0, 0)

    @Column(name = "target_tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    var targetTenantId: UUID = UUID(0, 0)

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, updatable = false, length = 30)
    var relationshipType: RelationshipType = RelationshipType.PARTNER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RelationshipStatus = RelationshipStatus.PENDING

    @Column(name = "initiated_by", nullable = false, updatable = false, columnDefinition = "uuid")
    var initiatedBy: UUID = UUID(0, 0)

    @Column(name = "accepted_by", columnDefinition = "uuid")
    var acceptedBy: UUID? = null

    @Column(columnDefinition = "TEXT")
    var note: String? = null

    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set

    val isActive: Boolean
        get() = status == RelationshipStatus.ACTIVE

    fun involves(tenantId: UUID): Boolean = sourceTenantId == tenantId || targetTenantId == tenantId

    fun otherEnd(tenantId: UUID): UUID =
        when (tenantId) {
            sourceTenantId -> targetTenantId
            targetTenantId -> sourceTenantId
            else -> throw IllegalArgumentException("Tenant $tenantId is not part of relationship $id")
        }
}
