package com.synopticengine.api.inventory.movement.web

import com.synopticengine.api.inventory.movement.domain.MovementType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class StockStateResponse(
    val productId: UUID,
    val locationId: UUID?,
    val warehouseId: UUID,
    val onHand: Int,
    val reserved: Int,
    val inTransit: Int,
    val damaged: Int,
    val available: Int,
)

data class ReserveRequest(
    @field:NotNull val productId: UUID,
    @field:NotNull val locationId: UUID,
    @field:Min(1) val qty: Int,
    val refDocType: String? = null,
    val refDocId: UUID? = null,
    val actorId: UUID? = null,
)

data class ReleaseRequest(
    @field:NotNull val productId: UUID,
    @field:NotNull val locationId: UUID,
    @field:Min(1) val qty: Int,
    val refDocType: String? = null,
    val refDocId: UUID? = null,
    val actorId: UUID? = null,
)

data class LowStockEntry(
    val productId: UUID,
    val productName: String,
    val sku: String?,
    val reorderThreshold: Int,
    val currentStock: Int,
)
