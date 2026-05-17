package com.synopticengine.api.settings.emailtemplate.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "email_templates")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class EmailTemplate : BaseEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(nullable = false)
    var subject: String = ""

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = ""

    @Column(nullable = false)
    var isPredefined: Boolean = false
}
