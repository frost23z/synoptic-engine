package com.synopticengine.api.identity.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Filter

@Entity
@Table(
    name = "groups",
    uniqueConstraints = [UniqueConstraint(name = "uq_groups_tenant_name", columnNames = ["tenant_id", "name"])],
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Group : BaseEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column
    var description: String? = null
}
