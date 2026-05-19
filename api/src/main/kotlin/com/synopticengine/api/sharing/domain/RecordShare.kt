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
import org.hibernate.annotations.SQLRestriction
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Per-record share — the ad-hoc complement to [TenantSharePolicy]. A user with
 * `records.share` permission on the owner side grants access to a single record.
 *
 * Soft-revocable via `revoked_at`; expirable via `expires_at`. Hard-delete only when
 * the underlying record is hard-deleted.
 */
@Entity
@Table(
    name = "record_shares",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_record_share",
            columnNames = ["owner_tenant_id", "consumer_tenant_id", "resource_type", "resource_id"],
        ),
    ],
)
@EntityListeners(AuditingEntityListener::class)
@SQLRestriction("revoked_at IS NULL")
class RecordShare {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(name = "owner_tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    var ownerTenantId: UUID = UUID(0, 0)

    @Column(name = "consumer_tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    var consumerTenantId: UUID = UUID(0, 0)

    @Column(name = "resource_type", nullable = false, updatable = false, length = 50)
    var resourceType: String = ""

    @Column(name = "resource_id", nullable = false, updatable = false, columnDefinition = "uuid")
    var resourceId: UUID = UUID(0, 0)

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    var accessLevel: AccessLevel = AccessLevel.READ

    @Column(name = "shared_by", nullable = false, updatable = false, columnDefinition = "uuid")
    var sharedBy: UUID = UUID(0, 0)

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(columnDefinition = "TEXT")
    var note: String? = null

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

    val isLive: Boolean
        get() = revokedAt == null && (expiresAt == null || expiresAt!!.isAfter(Instant.now()))
}
