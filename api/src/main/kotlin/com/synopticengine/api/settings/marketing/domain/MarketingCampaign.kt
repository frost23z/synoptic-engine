package com.synopticengine.api.settings.marketing.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

@Entity
@Table(name = "marketing_campaigns")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class MarketingCampaign : BaseEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(nullable = false)
    var subject: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "event_id")
    var eventId: UUID? = null

    @Column(name = "email_template_id")
    var emailTemplateId: UUID? = null
}
