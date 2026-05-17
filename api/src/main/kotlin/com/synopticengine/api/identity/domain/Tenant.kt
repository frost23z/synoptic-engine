package com.synopticengine.api.identity.domain

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
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * A tenant — a top-level isolation boundary. The tenant itself has no `tenant_id`;
 * everything else does and references this row.
 */
@Entity
@Table(
    name = "tenants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["slug"])],
)
@EntityListeners(AuditingEntityListener::class)
@SQLDelete(sql = "UPDATE tenants SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var slug: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TenantStatus = TenantStatus.ACTIVE

    @Column(name = "legal_name")
    var legalName: String? = null

    @Column
    var timezone: String? = null

    @Column
    var locale: String? = null

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

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
        get() = status == TenantStatus.ACTIVE && deletedAt == null
}
