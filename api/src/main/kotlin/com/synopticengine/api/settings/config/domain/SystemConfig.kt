package com.synopticengine.api.settings.config.domain

import com.synopticengine.api.shared.TenantContext
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.ParamDef
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Composite primary key for [SystemConfig]: `(tenantId, code)`. JPA requires the
 * IdClass to be Serializable with `equals` / `hashCode` over the same fields the
 * entity marks `@Id`.
 */
data class SystemConfigId(
    val tenantId: UUID = UUID(0L, 0L),
    val code: String = "",
) : Serializable

/**
 * Per-tenant configuration catalogue. The primary key is `(tenant_id, code)` so
 * each tenant has its own copy of every catalogue row. V005 created the table
 * with `code` as a single PK and a `tenant_id` column defaulting to the seed
 * tenant; V012 promotes `tenant_id` into the PK. See audit doc H9.
 *
 * Doesn't extend [com.synopticengine.api.shared.domain.BaseEntity] because that
 * superclass assumes a UUID `id` primary key. Tenant scoping is implemented via
 * Hibernate `@Filter` directly on this class, and `@PrePersist` defends against
 * inserts without an active [TenantContext].
 */
@Entity
@Table(name = "system_configs")
@IdClass(SystemConfigId::class)
@FilterDef(name = "tenantFilter", parameters = [ParamDef(name = "tenantId", type = UUID::class)])
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class SystemConfig {
    @Id
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid", updatable = false)
    var tenantId: UUID = TenantContext.SEED_TENANT_ID
        protected set

    @Id
    @Column(nullable = false)
    var code: String = ""

    @Column(columnDefinition = "TEXT")
    var value: String? = null

    @Column(nullable = false)
    var groupName: String = "general"

    @Column(nullable = false)
    var label: String = ""

    @Column(nullable = false)
    var type: String = "text"

    @Column(nullable = false)
    var isSecret: Boolean = false

    @Column(nullable = false)
    var sortOrder: Int = 0

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()

    @PrePersist
    private fun assignTenant() {
        tenantId =
            TenantContext.get()
                ?: error(
                    "TenantContext is not set. " +
                        "SystemConfig rows can only be created inside an authenticated request " +
                        "or a TenantContext.runAs { ... } block.",
                )
    }
}
