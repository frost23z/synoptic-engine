package com.synopticengine.api.inventory.warehouse.web

import com.synopticengine.api.crm.TagDto
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class MassDestroyWarehouseRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE warehouses at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class CreateWarehouseRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:Size(max = 1_000, message = "Description must not exceed 1 000 characters")
    val description: String? = null,
    @field:Size(max = 255, message = "Contact name must not exceed 255 characters")
    val contactName: String? = null,
    @field:Email(message = "Invalid contact email address")
    val contactEmail: String? = null,
    @field:Size(max = 50, message = "Contact phone must not exceed 50 characters")
    val contactPhone: String? = null,
    @field:Size(max = 500, message = "Contact address must not exceed 500 characters")
    val contactAddress: String? = null,
)

data class UpdateWarehouseRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:Size(max = 1_000, message = "Description must not exceed 1 000 characters")
    val description: String? = null,
    @field:Size(max = 255, message = "Contact name must not exceed 255 characters")
    val contactName: String? = null,
    @field:Email(message = "Invalid contact email address")
    val contactEmail: String? = null,
    @field:Size(max = 50, message = "Contact phone must not exceed 50 characters")
    val contactPhone: String? = null,
    @field:Size(max = 500, message = "Contact address must not exceed 500 characters")
    val contactAddress: String? = null,
)

data class WarehouseResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val contactName: String?,
    val contactEmail: String?,
    val contactPhone: String?,
    val contactAddress: String?,
    val tags: List<TagDto> = emptyList(),
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class TagAttachWarehouseRequest(
    @field:NotNull(message = "Tag ID is required")
    val tagId: UUID,
)

data class CreateLocationRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
)

data class UpdateLocationRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
)

data class WarehouseLocationResponse(
    val id: UUID,
    val warehouseId: UUID,
    val name: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

data class WarehouseProductEntry(
    val productId: UUID,
    val warehouseLocationId: UUID?,
    val quantity: Int,
)
