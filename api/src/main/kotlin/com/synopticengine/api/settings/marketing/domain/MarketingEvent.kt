package com.synopticengine.api.settings.marketing.domain

import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "marketing_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_marketing_events_tenant_name",
            columnNames = ["tenant_id", "name"],
        ),
    ],
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE marketing_events SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class MarketingEvent :
    BaseEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "event_date")
    var eventDate: LocalDate? = null

    @Column
    override var deletedAt: Instant? = null
}
