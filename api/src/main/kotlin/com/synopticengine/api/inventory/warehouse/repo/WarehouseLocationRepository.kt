package com.synopticengine.api.inventory.warehouse.repo

import com.synopticengine.api.inventory.warehouse.domain.WarehouseLocation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WarehouseLocationRepository : JpaRepository<WarehouseLocation, UUID> {
    fun findAllByWarehouseId(warehouseId: UUID): List<WarehouseLocation>

    fun findByIdAndWarehouseId(
        id: UUID,
        warehouseId: UUID,
    ): WarehouseLocation?
}
