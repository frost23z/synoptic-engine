package com.synopticengine.api.inventory.movement.web

import com.synopticengine.api.inventory.movement.service.InventoryMovementService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/inventory")
class InventoryMovementController(
    private val movementService: InventoryMovementService,
) {
    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('inventory.movements.view')")
    fun getStock(
        @RequestParam productId: UUID,
        @RequestParam(required = false) warehouseId: UUID?,
        @RequestParam(required = false) locationId: UUID?,
    ): ResponseEntity<List<StockStateResponse>> {
        if (warehouseId == null) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(movementService.getStockState(productId, warehouseId, locationId))
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasAuthority('inventory.movements.create')")
    fun reserve(
        @Valid @RequestBody request: ReserveRequest,
    ): ResponseEntity<Void> {
        movementService.reserve(
            request.productId,
            request.locationId,
            request.qty,
            request.refDocType,
            request.refDocId,
            request.actorId,
        )
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/release")
    @PreAuthorize("hasAuthority('inventory.movements.create')")
    fun release(
        @Valid @RequestBody request: ReleaseRequest,
    ): ResponseEntity<Void> {
        movementService.release(
            request.productId,
            request.locationId,
            request.qty,
            request.refDocType,
            request.refDocId,
            request.actorId,
        )
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('inventory.movements.view')")
    fun getMovements(
        @RequestParam productId: UUID,
    ): ResponseEntity<List<MovementResponse>> = ResponseEntity.ok(movementService.getMovements(productId))

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('inventory.reorder.view')")
    fun getLowStock(): ResponseEntity<List<LowStockEntry>> = ResponseEntity.ok(movementService.getLowStock())
}
