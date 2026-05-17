package com.synopticengine.api.inventory.warehouse.repo

import com.synopticengine.api.inventory.warehouse.domain.ProductInventory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProductInventoryRepository : JpaRepository<ProductInventory, UUID> {
    fun findAllByProductId(productId: UUID): List<ProductInventory>

    fun findAllByWarehouseId(warehouseId: UUID): List<ProductInventory>

    fun findByProductIdAndWarehouseIdAndWarehouseLocationId(
        productId: UUID,
        warehouseId: UUID,
        warehouseLocationId: UUID?,
    ): ProductInventory?
}
