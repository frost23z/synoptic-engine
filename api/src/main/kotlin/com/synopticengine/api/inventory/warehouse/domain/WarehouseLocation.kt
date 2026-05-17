package com.synopticengine.api.inventory.warehouse.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

@Entity
@Table(name = "warehouse_locations")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class WarehouseLocation : BaseEntity() {
    @Column(nullable = false)
    var warehouseId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var name: String = ""
}
