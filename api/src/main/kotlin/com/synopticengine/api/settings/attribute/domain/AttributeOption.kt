package com.synopticengine.api.settings.attribute.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

@Entity
@Table(name = "attribute_options")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class AttributeOption : BaseEntity() {
    @Column(name = "attribute_id", insertable = false, updatable = false)
    var attributeId: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_id", nullable = false)
    lateinit var attribute: Attribute

    @Column(nullable = false)
    var adminName: String = ""

    @Column(nullable = false)
    var sortOrder: Int = 0
}
