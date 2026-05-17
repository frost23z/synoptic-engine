package com.synopticengine.api.crm.lead.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "lead_products")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class LeadProduct : BaseEntity() {
    @Column(nullable = false)
    var leadId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var productId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var quantity: Int = 1

    @Column(precision = 15, scale = 2)
    var unitPrice: BigDecimal? = null
}
