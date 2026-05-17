package com.synopticengine.api.settings.attribute.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

@Entity
@Table(name = "attribute_values")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class AttributeValue : BaseEntity() {
    @Column(nullable = false)
    var attributeId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var entityId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var entityType: String = ""

    @Column(columnDefinition = "TEXT")
    var value: String? = null
}
