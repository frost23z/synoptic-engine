package com.synopticengine.api.crm.lead.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter

@Entity
@Table(name = "lead_sources")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class LeadSource : BaseEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""
}
