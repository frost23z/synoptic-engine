package com.synopticengine.api.inventory.transfer.web

import com.synopticengine.api.inventory.transfer.domain.TransferStatus
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateTransferRequest(
    @field:NotNull val fromLocationId: UUID,
    @field:NotNull val toLocationId: UUID,
    @field:NotNull val productId: UUID,
    @field:Min(1) val quantity: Int,
    val notes: String? = null,
)

data class TransferOrderResponse(
    val id: UUID,
    val fromLocationId: UUID,
    val toLocationId: UUID,
    val productId: UUID,
    val quantity: Int,
    val status: TransferStatus,
    val outMovementId: UUID?,
    val inMovementId: UUID?,
    val notes: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)
