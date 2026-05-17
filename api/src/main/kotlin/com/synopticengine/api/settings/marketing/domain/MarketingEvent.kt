package com.synopticengine.api.settings.marketing.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "marketing_events")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class MarketingEvent : BaseEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null
}
