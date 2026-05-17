package com.synopticengine.api.settings.automation.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "workflows")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Workflow : BaseEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "event_name", nullable = false)
    var eventName: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var conditions: List<Map<String, String>> = emptyList()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var actions: List<Map<String, String>> = emptyList()

    @Column(nullable = false)
    var isActive: Boolean = true
}
