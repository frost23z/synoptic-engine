package com.synopticengine.api.settings.webform.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "web_forms")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class WebForm : BaseEntity() {
    @Column(nullable = false)
    var title: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @OneToMany(mappedBy = "webForm", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    val fields: MutableList<WebFormAttribute> = mutableListOf()
}
