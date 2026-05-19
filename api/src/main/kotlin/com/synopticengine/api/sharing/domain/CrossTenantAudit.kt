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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Append-only log of every action where the actor's tenant differs from the record's
 * owner tenant. Owner queries `/api/cross-tenant-audit?resourceType=&resourceId=` to
 * see who touched their record.
 */
@Entity
@Table(name = "cross_tenant_audit")
@EntityListeners(AuditingEntityListener::class)
class CrossTenantAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(name = "owner_tenant_id", nullable = false, columnDefinition = "uuid")
    var ownerTenantId: UUID = UUID(0, 0)

    @Column(name = "actor_tenant_id", nullable = false, columnDefinition = "uuid")
    var actorTenantId: UUID = UUID(0, 0)

    @Column(name = "actor_user_id", nullable = false, columnDefinition = "uuid")
    var actorUserId: UUID = UUID(0, 0)

    @Column(name = "resource_type", nullable = false, length = 50)
    var resourceType: String = ""

    @Column(name = "resource_id", nullable = false, columnDefinition = "uuid")
    var resourceId: UUID = UUID(0, 0)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var action: CrossTenantAction = CrossTenantAction.VIEW

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_jsonb", columnDefinition = "jsonb")
    var payloadJson: String? = null

    @CreatedDate
    @Column(name = "at", nullable = false, updatable = false)
    var at: Instant? = null
        protected set
}
