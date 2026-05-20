package com.synopticengine.api.identity.domain

import com.synopticengine.api.shared.domain.GlobalCatalogEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "permissions")
class Permission : GlobalCatalogEntity() {
    @Column(nullable = false, unique = true)
    var key: String = ""

    @Column
    var description: String? = null
}
