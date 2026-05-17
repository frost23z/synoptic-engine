package com.synopticengine.api.settings.automation.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "webhooks")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Webhook : BaseEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "payload_url", nullable = false, length = 2048)
    var payloadUrl: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var events: List<String> = emptyList()

    @Column(nullable = false)
    var isActive: Boolean = true
}
