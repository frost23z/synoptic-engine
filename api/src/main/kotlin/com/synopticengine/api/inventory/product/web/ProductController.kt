package com.synopticengine.api.inventory.product.web

import com.synopticengine.api.crm.ActivityPage
import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.inventory.product.service.ProductService
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
@RequestMapping($$"${api.base-path}/products")
class ProductController(
    private val productService: ProductService,
    private val crmApi: CrmApi,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('products.view')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ProductResponse>> =
        ResponseEntity.ok(productService.findAll(PageRequest.of(page, size, Sort.by("name").ascending())))

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('products.view')")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ProductResponse>> =
        ResponseEntity.ok(productService.search(q, PageRequest.of(page, size, Sort.by("name").ascending())))

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('products.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<ProductResponse> = ResponseEntity.ok(productService.findById(id))

    @GetMapping("/{id}/inventory")
    @PreAuthorize("hasAuthority('products.view')")
    fun getInventory(
        @PathVariable id: UUID,
    ): ResponseEntity<List<InventoryEntryResponse>> = ResponseEntity.ok(productService.getInventory(id))

    @PostMapping
    @PreAuthorize("hasAuthority('products.create')")
    fun create(
        @Valid @RequestBody request: CreateProductRequest,
    ): ResponseEntity<ProductResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                productService.create(request.name, request.description, request.price, request.sku, request.isActive, request.reorderThreshold),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('products.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateProductRequest,
    ): ResponseEntity<ProductResponse> =
        ResponseEntity.ok(
            productService.update(id, request.name, request.description, request.price, request.sku, request.isActive, request.reorderThreshold),
        )

    @PutMapping("/{id}/inventory")
    @PreAuthorize("hasAuthority('products.edit')")
    fun setInventory(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SetInventoryRequest,
    ): ResponseEntity<InventoryEntryResponse> =
        ResponseEntity.ok(
            productService.setInventory(id, request.warehouseId, request.warehouseLocationId, request.quantity),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('products.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        productService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('products.delete')")
    fun massDestroy(
        @Valid @RequestBody request: MassDestroyProductRequest,
    ): ResponseEntity<Void> {
        productService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("hasAuthority('products.edit')")
    fun attachTag(
        @PathVariable id: UUID,
        @Valid @RequestBody request: TagAttachProductRequest,
    ): ResponseEntity<ProductResponse> = ResponseEntity.ok(productService.attachTag(id, request.tagId))

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("hasAuthority('products.edit')")
    fun detachTag(
        @PathVariable id: UUID,
        @PathVariable tagId: UUID,
    ): ResponseEntity<ProductResponse> = ResponseEntity.ok(productService.detachTag(id, tagId))

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority('products.view')")
    fun getActivities(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ActivityPage> = ResponseEntity.ok(crmApi.filterActivitiesByProductId(id, page, size))
}
