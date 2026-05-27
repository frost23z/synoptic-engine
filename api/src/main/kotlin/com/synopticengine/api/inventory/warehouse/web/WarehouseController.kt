package com.synopticengine.api.inventory.warehouse.web

import com.synopticengine.api.crm.ActivityPage
import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.inventory.warehouse.service.WarehouseService
import com.synopticengine.api.shared.web.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/warehouses")
class WarehouseController(
    private val warehouseService: WarehouseService,
    private val crmApi: CrmApi,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('warehouses.view')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<WarehouseResponse>> =
        ResponseEntity.ok(warehouseService.findAll(PageRequest.of(page, size, Sort.by("name").ascending())))

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('warehouses.view')")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<WarehouseResponse>> =
        ResponseEntity.ok(warehouseService.search(q, PageRequest.of(page, size, Sort.by("name").ascending())))

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouses.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<WarehouseResponse> = ResponseEntity.ok(warehouseService.findById(id))

    @GetMapping("/{id}/products")
    @PreAuthorize("hasAuthority('warehouses.view')")
    fun getProducts(
        @PathVariable id: UUID,
    ): ResponseEntity<List<WarehouseProductEntry>> = ResponseEntity.ok(warehouseService.getProducts(id))

    @GetMapping("/{id}/locations")
    @PreAuthorize("hasAuthority('warehouses.view')")
    fun getLocations(
        @PathVariable id: UUID,
    ): ResponseEntity<List<WarehouseLocationResponse>> = ResponseEntity.ok(warehouseService.getLocations(id))

    @PostMapping
    @PreAuthorize("hasAuthority('warehouses.create')")
    fun create(
        @Valid @RequestBody request: CreateWarehouseRequest,
    ): ResponseEntity<WarehouseResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                warehouseService.create(
                    request.name,
                    request.description,
                    request.contactName,
                    request.contactEmail,
                    request.contactPhone,
                    request.contactAddress,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouses.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWarehouseRequest,
    ): ResponseEntity<WarehouseResponse> =
        ResponseEntity.ok(
            warehouseService.update(
                id,
                request.name,
                request.description,
                request.contactName,
                request.contactEmail,
                request.contactPhone,
                request.contactAddress,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('warehouses.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        warehouseService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('warehouses.delete')")
    fun massDestroy(
        @Valid @RequestBody request: MassDestroyWarehouseRequest,
    ): ResponseEntity<Void> {
        warehouseService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/locations")
    @PreAuthorize("hasAuthority('warehouses.edit')")
    fun addLocation(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateLocationRequest,
    ): ResponseEntity<WarehouseLocationResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(warehouseService.addLocation(id, request.name))

    @PutMapping("/{id}/locations/{locationId}")
    @PreAuthorize("hasAuthority('warehouses.edit')")
    fun updateLocation(
        @PathVariable id: UUID,
        @PathVariable locationId: UUID,
        @Valid @RequestBody request: UpdateLocationRequest,
    ): ResponseEntity<WarehouseLocationResponse> =
        ResponseEntity.ok(warehouseService.updateLocation(id, locationId, request.name))

    @DeleteMapping("/{id}/locations/{locationId}")
    @PreAuthorize("hasAuthority('warehouses.delete')")
    fun deleteLocation(
        @PathVariable id: UUID,
        @PathVariable locationId: UUID,
    ): ResponseEntity<Void> {
        warehouseService.deleteLocation(id, locationId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('warehouses.edit')")
    fun attachTag(
        @PathVariable id: UUID,
        @Valid @RequestBody request: TagAttachWarehouseRequest,
    ): ResponseEntity<WarehouseResponse> = ResponseEntity.ok(warehouseService.attachTag(id, request.tagId))

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("hasAuthority('warehouses.edit')")
    fun detachTag(
        @PathVariable id: UUID,
        @PathVariable tagId: UUID,
    ): ResponseEntity<WarehouseResponse> = ResponseEntity.ok(warehouseService.detachTag(id, tagId))

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('warehouses.view')")
    fun getActivities(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ActivityPage> = ResponseEntity.ok(crmApi.filterActivitiesByWarehouseId(id, page, size))
}
