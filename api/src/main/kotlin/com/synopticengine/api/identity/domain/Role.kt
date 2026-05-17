package com.synopticengine.api.identity.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "roles")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Role : BaseEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column
    var description: String? = null

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")],
    )
    val permissions: MutableSet<Permission> = mutableSetOf()
}
