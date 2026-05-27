package com.synopticengine.api.inventory.product.web

import com.synopticengine.api.crm.TagDto
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
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
    @field:NotNull(message = "Tag ID is required")
    val tagId: UUID,
)

data class SetInventoryRequest(
    @field:NotNull(message = "Warehouse ID is required")
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
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE products at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}
