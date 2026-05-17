package com.synopticengine.api.inventory.product.web

import com.synopticengine.api.crm.TagDto
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    @field:DecimalMin("0.0", message = "Price must be non-negative")
    val price: BigDecimal = BigDecimal.ZERO,
    val sku: String? = null,
    val isActive: Boolean = true,
)

data class UpdateProductRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    @field:DecimalMin("0.0", message = "Price must be non-negative")
    val price: BigDecimal = BigDecimal.ZERO,
    val sku: String? = null,
    val isActive: Boolean = true,
)

data class ProductResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val sku: String?,
    val isActive: Boolean,
    val tags: List<TagDto> = emptyList(),
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class TagAttachProductRequest(
    val tagId: UUID,
)

data class SetInventoryRequest(
    val warehouseId: UUID,
    val warehouseLocationId: UUID? = null,
    val quantity: Int,
)

data class InventoryEntryResponse(
    val id: UUID,
    val productId: UUID,
    val warehouseId: UUID,
    val warehouseLocationId: UUID?,
    val quantity: Int,
)

data class MassDestroyProductRequest(
    val ids: List<UUID>,
)
