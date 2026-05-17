package com.synopticengine.api.settings.emailtemplate.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Filter

@Entity
@Table(
    name = "email_templates",
    uniqueConstraints = [UniqueConstraint(name = "uq_email_templates_tenant_name", columnNames = ["tenant_id", "name"])],
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class EmailTemplate : BaseEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var subject: String = ""

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = ""

    @Column(nullable = false)
    var isPredefined: Boolean = false
}
