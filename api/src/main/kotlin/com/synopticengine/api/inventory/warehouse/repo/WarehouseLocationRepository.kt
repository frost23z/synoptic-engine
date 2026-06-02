package com.synopticengine.api.inventory.warehouse.repo

import com.synopticengine.api.inventory.warehouse.domain.WarehouseLocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WarehouseLocationRepository : JpaRepository<WarehouseLocation, UUID> {
    fun findAllByWarehouseId(warehouseId: UUID): List<WarehouseLocation>

    fun findByIdAndWarehouseId(
        id: UUID,
        warehouseId: UUID,
    ): WarehouseLocation?

    @Query("SELECT l FROM WarehouseLocation l WHERE l.warehouseId = :warehouseId AND l.deletedAt IS NULL")
    fun findAllByWarehouseIdAndDeletedAtIsNull(
        @Param("warehouseId") warehouseId: UUID,
    ): List<WarehouseLocation>

    @Query(
        "SELECT l FROM WarehouseLocation l WHERE l.id = :id AND l.warehouseId = :warehouseId AND l.deletedAt IS NULL",
    )
    fun findByIdAndWarehouseIdAndDeletedAtIsNull(
        @Param("id") id: UUID,
        @Param("warehouseId") warehouseId: UUID,
    ): WarehouseLocation?

    fun findByIdAndDeletedAtIsNull(id: UUID): WarehouseLocation?
}
