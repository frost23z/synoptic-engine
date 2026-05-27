package com.synopticengine.api.inventory.transfer.service

import com.synopticengine.api.inventory.movement.domain.InventoryMovement
import com.synopticengine.api.inventory.movement.domain.MovementType
import com.synopticengine.api.inventory.movement.repo.InventoryMovementRepository
import com.synopticengine.api.inventory.movement.service.InventoryMovementService
import com.synopticengine.api.inventory.transfer.domain.TransferOrder
import com.synopticengine.api.inventory.transfer.domain.TransferStatus
import com.synopticengine.api.inventory.transfer.repo.TransferOrderRepository
import com.synopticengine.api.inventory.transfer.web.TransferOrderResponse
import com.synopticengine.api.inventory.warehouse.repo.WarehouseLocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class TransferService(
    private val transferRepository: TransferOrderRepository,
    private val movementRepository: InventoryMovementRepository,
    private val inventoryMovementService: InventoryMovementService,
    private val locationRepository: WarehouseLocationRepository,
) {
    fun findAll(): List<TransferOrderResponse> = transferRepository.findAllByDeletedAtIsNull().map { it.toResponse() }

    @Transactional
    fun create(
        fromLocationId: UUID,
        toLocationId: UUID,
        productId: UUID,
        qty: Int,
        notes: String?,
    ): TransferOrderResponse {
        val fromLocation =
            locationRepository.findByIdAndDeletedAtIsNull(fromLocationId)
                ?: throw NoSuchElementException("From-location not found: $fromLocationId")
        val inv =
            inventoryMovementService.findOrCreate(productId, fromLocation.warehouseId, fromLocationId)
        check(inv.onHand >= qty) {
            "Insufficient on-hand stock: onHand=${inv.onHand}, requested=$qty"
        }
        return transferRepository
            .save(
                TransferOrder().apply {
                    this.fromLocationId = fromLocationId
                    this.toLocationId = toLocationId
                    this.productId = productId
                    this.quantity = qty
                    this.status = TransferStatus.PENDING
                    this.notes = notes
                },
            ).toResponse()
    }

    @Transactional
    fun dispatch(orderId: UUID): TransferOrderResponse {
        val order = requireOrder(orderId)
        check(order.status == TransferStatus.PENDING) { "Order must be PENDING to dispatch" }
        val fromLocation =
            locationRepository.findByIdAndDeletedAtIsNull(order.fromLocationId)
                ?: throw NoSuchElementException("From-location not found: ${order.fromLocationId}")
        val inv = inventoryMovementService.findOrCreate(order.productId, fromLocation.warehouseId, order.fromLocationId)
        check(inv.onHand >= order.quantity) {
            "Insufficient on-hand stock at dispatch: onHand=${inv.onHand}, requested=${order.quantity}"
        }
        inv.onHand -= order.quantity
        inv.inTransit += order.quantity
        val outMovement =
            movementRepository.save(
                InventoryMovement().apply {
                    productId = order.productId
                    movementType = MovementType.TRANSFER_OUT
                    fromLocationId = order.fromLocationId
                    quantity = order.quantity
                },
            )
        order.status = TransferStatus.IN_TRANSIT
        order.outMovementId = outMovement.id
        return transferRepository.save(order).toResponse()
    }

    @Transactional
    fun receive(orderId: UUID): TransferOrderResponse {
        val order = requireOrder(orderId)
        check(order.status == TransferStatus.IN_TRANSIT) { "Order must be IN_TRANSIT to receive" }
        val fromLocation =
            locationRepository.findByIdAndDeletedAtIsNull(order.fromLocationId)
                ?: throw NoSuchElementException("From-location not found: ${order.fromLocationId}")
        val toLocation =
            locationRepository.findByIdAndDeletedAtIsNull(order.toLocationId)
                ?: throw NoSuchElementException("To-location not found: ${order.toLocationId}")
        val fromInv = inventoryMovementService.findOrCreate(order.productId, fromLocation.warehouseId, order.fromLocationId)
        fromInv.inTransit = maxOf(0, fromInv.inTransit - order.quantity)
        val toInv = inventoryMovementService.findOrCreate(order.productId, toLocation.warehouseId, order.toLocationId)
        toInv.onHand += order.quantity
        val inMovement =
            movementRepository.save(
                InventoryMovement().apply {
                    productId = order.productId
                    movementType = MovementType.TRANSFER_IN
                    toLocationId = order.toLocationId
                    quantity = order.quantity
                },
            )
        order.status = TransferStatus.COMPLETED
        order.inMovementId = inMovement.id
        return transferRepository.save(order).toResponse()
    }

    @Transactional
    fun cancel(orderId: UUID): TransferOrderResponse {
        val order = requireOrder(orderId)
        check(order.status == TransferStatus.PENDING) { "Only PENDING orders can be cancelled" }
        order.status = TransferStatus.CANCELLED
        return transferRepository.save(order).toResponse()
    }

    private fun requireOrder(id: UUID): TransferOrder =
        transferRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw NoSuchElementException("Transfer order not found: $id")
}

fun TransferOrder.toResponse() =
    TransferOrderResponse(
        id = id!!,
        fromLocationId = fromLocationId,
        toLocationId = toLocationId,
        productId = productId,
        quantity = quantity,
        status = status,
        outMovementId = outMovementId,
        inMovementId = inMovementId,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
