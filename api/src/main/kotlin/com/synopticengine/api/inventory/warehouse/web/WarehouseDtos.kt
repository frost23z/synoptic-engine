package com.synopticengine.api.inventory.warehouse.web

import com.synopticengine.api.crm.TagDto
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class MassDestroyWarehouseRequest(
    val ids: List<UUID>,
)

data class CreateWarehouseRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val contactAddress: String? = null,
)

data class UpdateWarehouseRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val description: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
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
