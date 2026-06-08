package com.synopticengine.api.inventory.transfer.web

import com.synopticengine.api.inventory.transfer.service.TransferService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/inventory/transfers")
class TransferController(
    private val transferService: TransferService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('inventory.transfers.view')")
    fun listAll(): ResponseEntity<List<TransferOrderResponse>> = ResponseEntity.ok(transferService.findAll())

    @PostMapping
    @PreAuthorize("hasAuthority('inventory.transfers.create')")
    fun create(
        @Valid @RequestBody request: CreateTransferRequest,
    ): ResponseEntity<TransferOrderResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                transferService.create(
                    request.fromLocationId,
                    request.toLocationId,
                    request.productId,
                    request.quantity,
                    request.notes,
                ),
            )

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('inventory.transfers.manage')")
    fun dispatch(
        @PathVariable id: UUID,
    ): ResponseEntity<TransferOrderResponse> = ResponseEntity.ok(transferService.dispatch(id))

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('inventory.transfers.manage')")
    fun receive(
        @PathVariable id: UUID,
    ): ResponseEntity<TransferOrderResponse> = ResponseEntity.ok(transferService.receive(id))

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('inventory.transfers.manage')")
    fun cancel(
        @PathVariable id: UUID,
    ): ResponseEntity<TransferOrderResponse> = ResponseEntity.ok(transferService.cancel(id))
}
