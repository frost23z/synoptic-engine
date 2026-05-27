package com.synopticengine.api.inventory.warehouse.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "product_inventories")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class ProductInventory : BaseEntity() {
    @Column(nullable = false)
    var productId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var warehouseId: UUID = UUID.randomUUID()

    @Column
    var warehouseLocationId: UUID? = null

    @Column(name = "on_hand", nullable = false)
    var onHand: Int = 0

    @Column(nullable = false)
    var reserved: Int = 0

    @Column(name = "in_transit", nullable = false)
    var inTransit: Int = 0

    @Column(nullable = false)
    var damaged: Int = 0

    @Column(name = "unit_cost", precision = 15, scale = 4)
    var unitCost: BigDecimal = BigDecimal.ZERO
}
