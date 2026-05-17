package com.synopticengine.api.crm.datagrid.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "datagrid_saved_filters")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class DataGridSavedFilter : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID()

    @Column(nullable = false, length = 255)
    var name: String = ""

    @Column(nullable = false, length = 100)
    var src: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var applied: Map<String, Any> = emptyMap()
}
