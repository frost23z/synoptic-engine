package com.synopticengine.api.shared.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Append-only record of sensitive same-tenant mutations (system_configs,
 * webhooks). Intentionally does NOT extend [com.synopticengine.api.shared.domain.BaseEntity]:
 *
 *  • `tenant_id` is an explicit column — no `@Filter` so the write path is not
 *    affected by filter state on async threads.
 *  • No `@Version` — rows are never updated.
 *  • No soft-delete — compliance rows must be immutable.
 *
 * See [AuditLogService] for the write API.
 */
@Entity
@Table(name = "audit_logs")
class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    var tenantId: UUID = UUID(0, 0)

    /** User ID of the actor; null for system/scheduled actions. */
    @Column(name = "actor_id", columnDefinition = "uuid")
    var actorId: UUID? = null

    @Column(name = "entity_type", nullable = false, length = 100)
    var entityType: String = ""

    /** UUID or business key (e.g. config code). Null for bulk operations. */
    @Column(name = "entity_id", length = 255)
    var entityId: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var action: AuditAction = AuditAction.UPDATE

    /** Sanitized change summary as JSON (no secret values). Null for DELETE. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var payload: Map<String, Any?>? = null

    @Column(nullable = false, columnDefinition = "timestamptz")
    var at: Instant = Instant.now()
}
