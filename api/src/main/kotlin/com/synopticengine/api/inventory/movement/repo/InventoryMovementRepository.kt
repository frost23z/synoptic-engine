package com.synopticengine.api.inventory.movement.repo

import com.synopticengine.api.inventory.movement.domain.InventoryMovement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface InventoryMovementRepository : JpaRepository<InventoryMovement, UUID> {
    @Query("SELECT m FROM InventoryMovement m WHERE m.productId = :productId ORDER BY m.createdAt DESC")
    fun findAllByProductId(
        @Param("productId") productId: UUID,
    ): List<InventoryMovement>
}
