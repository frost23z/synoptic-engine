package com.synopticengine.api.settings.webform.domain

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
@Table(name = "web_form_attributes")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class WebFormAttribute : BaseEntity() {
    @Column(name = "web_form_id", insertable = false, updatable = false)
    var webFormId: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "web_form_id", nullable = false)
    lateinit var webForm: WebForm

    @Column(nullable = false)
    var attributeId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var sortOrder: Int = 0

    @Column(nullable = false)
    var isRequired: Boolean = false
}
