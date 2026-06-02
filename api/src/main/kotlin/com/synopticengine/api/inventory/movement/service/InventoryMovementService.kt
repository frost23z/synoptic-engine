package com.synopticengine.api.inventory.movement.service

import com.synopticengine.api.inventory.movement.domain.InventoryMovement
import com.synopticengine.api.inventory.movement.domain.MovementType
import com.synopticengine.api.inventory.movement.repo.InventoryMovementRepository
import com.synopticengine.api.inventory.movement.web.LowStockEntry
import com.synopticengine.api.inventory.movement.web.StockStateResponse
import com.synopticengine.api.inventory.product.repo.ProductRepository
import com.synopticengine.api.inventory.warehouse.domain.ProductInventory
import com.synopticengine.api.inventory.warehouse.repo.ProductInventoryRepository
import com.synopticengine.api.inventory.warehouse.repo.WarehouseLocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
@Transactional(readOnly = true)
class InventoryMovementService(
    private val movementRepository: InventoryMovementRepository,
    private val inventoryRepository: ProductInventoryRepository,
    private val locationRepository: WarehouseLocationRepository,
    private val productRepository: ProductRepository,
) {
    fun getStockState(
        productId: UUID,
        warehouseId: UUID,
        locationId: UUID?,
    ): List<StockStateResponse> {
        val entries =
            if (locationId != null) {
                listOfNotNull(
                    inventoryRepository.findByProductIdAndWarehouseIdAndWarehouseLocationId(
                        productId,
                        warehouseId,
                        locationId,
                    ),
                )
            } else {
                inventoryRepository.findAllByWarehouseId(warehouseId).filter { it.productId == productId }
            }
        return entries.map { it.toStockStateResponse() }
    }

    fun getLowStock(): List<LowStockEntry> =
        productRepository
            .findAllByDeletedAtIsNull(
                org.springframework.data.domain.Pageable
                    .unpaged(),
            ).content
            .filter { it.reorderThreshold != null }
            .mapNotNull { product ->
                val totalOnHand =
                    inventoryRepository
                        .findAllByProductId(product.id!!)
                        .sumOf { it.onHand }
                if (totalOnHand <= product.reorderThreshold!!) {
                    LowStockEntry(
                        productId = product.id!!,
                        productName = product.name,
                        sku = product.sku,
                        reorderThreshold = product.reorderThreshold!!,
                        currentStock = totalOnHand,
                    )
                } else {
                    null
                }
            }

    @Transactional
    fun reserve(
        productId: UUID,
        locationId: UUID,
        qty: Int,
        refDocType: String?,
        refDocId: UUID?,
        actorId: UUID?,
    ) {
        val location =
            locationRepository.findByIdAndDeletedAtIsNull(locationId)
                ?: throw NoSuchElementException("Location not found: $locationId")
        val inv = findOrCreate(productId, location.warehouseId, locationId)
        check(inv.onHand - inv.reserved >= qty) {
            "Insufficient available stock: available=${inv.onHand - inv.reserved}, requested=$qty"
        }
        inv.reserved += qty
        inventoryRepository.save(inv)
        movementRepository.save(
            InventoryMovement().apply {
                this.productId = productId
                this.movementType = MovementType.RESERVE
                this.toLocationId = locationId
                this.quantity = qty
                this.refDocType = refDocType
                this.refDocId = refDocId
                this.actorId = actorId
            },
        )
    }

    @Transactional
    fun release(
        productId: UUID,
        locationId: UUID,
        qty: Int,
        refDocType: String?,
        refDocId: UUID?,
        actorId: UUID?,
    ) {
        val location =
            locationRepository.findByIdAndDeletedAtIsNull(locationId)
                ?: throw NoSuchElementException("Location not found: $locationId")
        val inv = findOrCreate(productId, location.warehouseId, locationId)
        inv.reserved = maxOf(0, inv.reserved - qty)
        inventoryRepository.save(inv)
        movementRepository.save(
            InventoryMovement().apply {
                this.productId = productId
                this.movementType = MovementType.RELEASE
                this.fromLocationId = locationId
                this.quantity = qty
                this.refDocType = refDocType
                this.refDocId = refDocId
                this.actorId = actorId
            },
        )
    }

    fun findOrCreate(
        productId: UUID,
        warehouseId: UUID,
        locationId: UUID?,
    ): ProductInventory =
        inventoryRepository.findByProductIdAndWarehouseIdAndWarehouseLocationId(productId, warehouseId, locationId)
            ?: inventoryRepository.save(
                ProductInventory().apply {
                    this.productId = productId
                    this.warehouseId = warehouseId
                    this.warehouseLocationId = locationId
                },
            )

    fun applyReceipt(
        inv: ProductInventory,
        qty: Int,
        unitCost: BigDecimal,
    ) {
        val newOnHand = inv.onHand + qty
        inv.unitCost =
            if (newOnHand > 0) {
                (inv.onHand.toBigDecimal() * inv.unitCost + qty.toBigDecimal() * unitCost)
                    .divide(newOnHand.toBigDecimal(), 4, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
        inv.onHand = newOnHand
    }
}

private fun ProductInventory.toStockStateResponse() =
    StockStateResponse(
        productId = productId,
        locationId = warehouseLocationId,
        warehouseId = warehouseId,
        onHand = onHand,
        reserved = reserved,
        inTransit = inTransit,
        damaged = damaged,
        available = onHand - reserved,
    )
