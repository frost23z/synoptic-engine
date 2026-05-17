package com.synopticengine.api.crm.tag.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "tags")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Tag : BaseEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column
    var color: String? = null
}
