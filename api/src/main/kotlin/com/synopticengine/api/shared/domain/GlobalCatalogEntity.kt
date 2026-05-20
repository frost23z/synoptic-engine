package com.synopticengine.api.shared.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Mapped superclass for entities that are **global catalogues** — no `tenant_id` column,
 * never scoped to a tenant. Only `Permission` and `Tenant` qualify today: both are
 * tenant-wide reference data that every tenant's rows point at.
 *
 * Anything else with rows owned by a tenant must extend [BaseEntity], whose
 * `@PrePersist` will refuse to insert without an active [com.synopticengine.api.shared.TenantContext].
 *
 * This consolidates what used to be three hand-rolled `@Id / @Version / @CreatedDate / @LastModifiedDate`
 * pairs (P3-4 from `analysis/08-phase-4-findings.md`).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class GlobalCatalogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

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
