package com.synopticengine.api.crm.lead.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Filter

@Entity
@Table(
    name = "lead_sources",
    uniqueConstraints = [UniqueConstraint(name = "uq_lead_sources_tenant_name", columnNames = ["tenant_id", "name"])],
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class LeadSource : BaseEntity() {
    @Column(nullable = false)
    var name: String = ""
}
