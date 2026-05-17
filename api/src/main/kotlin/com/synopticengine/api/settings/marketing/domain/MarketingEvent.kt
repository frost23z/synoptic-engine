package com.synopticengine.api.settings.marketing.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Filter

@Entity
@Table(
    name = "marketing_events",
    uniqueConstraints = [UniqueConstraint(name = "uq_marketing_events_tenant_name", columnNames = ["tenant_id", "name"])],
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class MarketingEvent : BaseEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null
}
