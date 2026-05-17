package com.synopticengine.api.inventory.product.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Filter
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "products",
    uniqueConstraints = [UniqueConstraint(name = "uq_products_tenant_sku", columnNames = ["tenant_id", "sku"])],
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Product :
    AuditableEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(precision = 15, scale = 2, nullable = false)
    var price: BigDecimal = BigDecimal.ZERO

    @Column
    var sku: String? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    override var deletedAt: Instant? = null
}
