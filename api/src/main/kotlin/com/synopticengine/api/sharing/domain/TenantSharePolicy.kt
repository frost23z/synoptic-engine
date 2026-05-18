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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Policy = "share all of resource X within relationship R at access level L".
 *
 * - `filter_jsonb` optionally narrows the policy to a subset of records.
 * - `cascade_jsonb` overrides the default cascade behaviour (see § 7).
 * - `materialize` defaults to true (rows go into `resource_visibility`); set false for
 *   very high-volume policies that prefer virtual evaluation (§ 5.3).
 */
@Entity
@Table(
    name = "tenant_share_policies",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_share_policy",
            columnNames = ["relationship_id", "resource_type"],
        ),
    ],
)
@EntityListeners(AuditingEntityListener::class)
@SQLRestriction("revoked_at IS NULL")
class TenantSharePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(name = "relationship_id", nullable = false, updatable = false, columnDefinition = "uuid")
    var relationshipId: UUID = UUID(0, 0)

    @Column(name = "resource_type", nullable = false, updatable = false, length = 50)
    var resourceType: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    var accessLevel: AccessLevel = AccessLevel.READ

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_jsonb", columnDefinition = "jsonb")
    var filterJson: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cascade_jsonb", columnDefinition = "jsonb")
    var cascadeJson: String? = null

    @Column(nullable = false)
    var materialize: Boolean = true

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    var createdBy: UUID = UUID(0, 0)

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
}
