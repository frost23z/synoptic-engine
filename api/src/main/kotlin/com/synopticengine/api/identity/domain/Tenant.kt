package com.synopticengine.api.identity.domain

import com.synopticengine.api.shared.domain.GlobalCatalogEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

/**
 * A tenant — a top-level isolation boundary. The tenant itself has no `tenant_id`;
 * everything else does and references this row.
 */
@Entity
@Table(
    name = "tenants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["slug"])],
)
@SQLDelete(sql = "UPDATE tenants SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Tenant : GlobalCatalogEntity() {
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

    val isActive: Boolean
        get() = status == TenantStatus.ACTIVE && deletedAt == null
}
