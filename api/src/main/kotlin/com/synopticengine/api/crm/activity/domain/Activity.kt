package com.synopticengine.api.crm.activity.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "activities")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE activities SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Activity :
    AuditableEntity(),
    SoftDeletable {
    @Column
    var leadId: UUID? = null

    @Column
    var userId: UUID? = null

    @Column
    var personId: UUID? = null

    @Column
    var organizationId: UUID? = null

    @Column
    var productId: UUID? = null

    @Column
    var warehouseId: UUID? = null

    @Column(nullable = false)
    var title: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: ActivityType = ActivityType.TASK

    @Column
    var location: String? = null

    /** Free-form JSON for activity-type-specific metadata. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var additional: String? = null

    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Column(nullable = false)
    var isDone: Boolean = false

    @Column
    var scheduleFrom: Instant? = null

    @Column
    var scheduleTo: Instant? = null

    @Column
    override var deletedAt: Instant? = null
}
