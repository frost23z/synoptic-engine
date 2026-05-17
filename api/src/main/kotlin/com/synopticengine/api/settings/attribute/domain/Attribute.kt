package com.synopticengine.api.settings.attribute.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "attributes")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Attribute : BaseEntity() {
    @Column(nullable = false)
    var code: String = ""

    @Column(nullable = false)
    var adminName: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AttributeType = AttributeType.TEXT

    @Column(nullable = false)
    var isUserDefined: Boolean = true

    @Column
    var lookup: String? = null

    @Column(nullable = false)
    var entityType: String = ""

    @Column(nullable = false)
    var sortOrder: Int = 0

    @OneToMany(mappedBy = "attribute", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    val options: MutableList<AttributeOption> = mutableListOf()
}
