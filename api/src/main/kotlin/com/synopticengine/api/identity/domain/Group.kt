package com.synopticengine.api.identity.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "groups")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Group : BaseEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column
    var description: String? = null
}
