package com.synopticengine.api.crm.activity.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "activities")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
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

    @Column(columnDefinition = "TEXT")
    var comment: String? = null

    @Column(nullable = false)
    var isDone: Boolean = false

    @Column(nullable = false)
    var scheduleFrom: Instant = Instant.now()

    @Column(nullable = false)
    var scheduleTo: Instant = Instant.now()

    @Column
    override var deletedAt: Instant? = null

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "activity_participants", joinColumns = [JoinColumn(name = "activity_id")])
    @Column(name = "user_id")
    val participantIds: MutableSet<UUID> = mutableSetOf()
}
