package com.synopticengine.api.inventory.movement.web

import com.synopticengine.api.inventory.movement.domain.MovementType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** A single append-only ledger entry (`GET /api/inventory/movements`). */
data class MovementResponse(
    val id: UUID,
    val productId: UUID,
    val movementType: MovementType,
    val fromLocationId: UUID?,
    val toLocationId: UUID?,
    val quantity: Int,
    val unitCost: BigDecimal?,
    val refDocType: String?,
    val refDocId: UUID?,
    val actorId: UUID?,
    val notes: String?,
    val createdAt: Instant?,
)

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
