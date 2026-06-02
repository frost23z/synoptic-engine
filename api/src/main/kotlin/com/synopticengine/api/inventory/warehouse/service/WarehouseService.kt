package com.synopticengine.api.inventory.warehouse.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.crm.TagDto
import com.synopticengine.api.inventory.warehouse.domain.Warehouse
import com.synopticengine.api.inventory.warehouse.domain.WarehouseLocation
import com.synopticengine.api.inventory.warehouse.repo.ProductInventoryRepository
import com.synopticengine.api.inventory.warehouse.repo.WarehouseLocationRepository
import com.synopticengine.api.inventory.warehouse.repo.WarehouseRepository
import com.synopticengine.api.inventory.warehouse.repo.WarehouseTagRepository
import com.synopticengine.api.inventory.warehouse.web.WarehouseLocationResponse
import com.synopticengine.api.inventory.warehouse.web.WarehouseProductEntry
import com.synopticengine.api.inventory.warehouse.web.WarehouseResponse
import com.synopticengine.api.shared.security.requireOwnership
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class WarehouseService(
    private val warehouseRepository: WarehouseRepository,
    private val locationRepository: WarehouseLocationRepository,
    private val inventoryRepository: ProductInventoryRepository,
    private val warehouseTagRepository: WarehouseTagRepository,
    private val crmApi: CrmApi,
) {
    fun findAll(pageable: Pageable): PageResponse<WarehouseResponse> =
        PageResponse.of(warehouseRepository.findAllByDeletedAtIsNull(pageable)) { it.toResponse(loadTags(it.id!!)) }

    fun findById(id: UUID): WarehouseResponse {
        val warehouse =
            warehouseRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw NoSuchElementException("Warehouse not found: $id")
        warehouse.requireOwnership()
        return warehouse.toResponse(loadTags(id))
    }

    fun search(
        q: String,
        pageable: Pageable,
    ): PageResponse<WarehouseResponse> =
        PageResponse.of(warehouseRepository.search(q, pageable)) { it.toResponse(loadTags(it.id!!)) }

    private fun loadTags(warehouseId: UUID): List<TagDto> =
        warehouseTagRepository.findTagIdsByWarehouseId(warehouseId).let { crmApi.findTagsByIds(it) }

    fun getProducts(warehouseId: UUID): List<WarehouseProductEntry> {
        requireWarehouse(warehouseId)
        return inventoryRepository.findAllByWarehouseId(warehouseId).map {
            WarehouseProductEntry(
                productId = it.productId,
                warehouseLocationId = it.warehouseLocationId,
                quantity = it.onHand,
            )
        }
    }

    fun getLocations(warehouseId: UUID): List<WarehouseLocationResponse> {
        requireWarehouse(warehouseId)
        return locationRepository.findAllByWarehouseIdAndDeletedAtIsNull(warehouseId).map { it.toResponse() }
    }

    @Transactional
    fun create(
        name: String,
        description: String?,
        contactName: String?,
        contactEmail: String?,
        contactPhone: String?,
        contactAddress: String?,
    ): WarehouseResponse =
        warehouseRepository
            .save(
                Warehouse().apply {
                    this.name = name
                    this.description = description
                    this.contactName = contactName
                    this.contactEmail = contactEmail
                    this.contactPhone = contactPhone
                    this.contactAddress = contactAddress
                },
            ).let { findById(it.id!!) }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        description: String?,
        contactName: String?,
        contactEmail: String?,
        contactPhone: String?,
        contactAddress: String?,
    ): WarehouseResponse {
        val warehouse = requireWarehouse(id)
        warehouse.name = name
        warehouse.description = description
        warehouse.contactName = contactName
        warehouse.contactEmail = contactEmail
        warehouse.contactPhone = contactPhone
        warehouse.contactAddress = contactAddress
        warehouseRepository.save(warehouse)
        return findById(id)
    }

    @Transactional
    fun delete(id: UUID) {
        val warehouse = requireWarehouse(id)
        warehouse.deletedAt = Instant.now()
        warehouseRepository.save(warehouse)
    }

    @Transactional
    fun addLocation(
        warehouseId: UUID,
        name: String,
    ): WarehouseLocationResponse {
        requireWarehouse(warehouseId)
        return locationRepository
            .save(
                WarehouseLocation().apply {
                    this.warehouseId = warehouseId
                    this.name = name
                },
            ).toResponse()
    }

    @Transactional
    fun updateLocation(
        warehouseId: UUID,
        locationId: UUID,
        name: String,
    ): WarehouseLocationResponse {
        val location =
            locationRepository.findByIdAndWarehouseIdAndDeletedAtIsNull(locationId, warehouseId)
                ?: throw NoSuchElementException("Location not found: $locationId")
        location.name = name
        return locationRepository.save(location).toResponse()
    }

    @Transactional
    fun deleteLocation(
        warehouseId: UUID,
        locationId: UUID,
    ) {
        val location =
            locationRepository.findByIdAndWarehouseIdAndDeletedAtIsNull(locationId, warehouseId)
                ?: throw NoSuchElementException("Location not found: $locationId")
        val stock =
            inventoryRepository
                .findAllByWarehouseId(warehouseId)
                .filter { it.warehouseLocationId == locationId }
                .sumOf { it.onHand + it.reserved + it.inTransit }
        check(stock == 0) { "Cannot delete location: stock present" }
        location.deletedAt = java.time.Instant.now()
        locationRepository.save(location)
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            warehouseRepository.findByIdAndDeletedAtIsNull(id)?.let { warehouse ->
                warehouse.deletedAt = Instant.now()
                warehouseRepository.save(warehouse)
            }
        }
    }

    @Transactional
    fun attachTag(
        warehouseId: UUID,
        tagId: UUID,
    ): WarehouseResponse {
        requireWarehouse(warehouseId)
        if (!crmApi.tagExists(tagId)) throw NoSuchElementException("Tag not found: $tagId")
        warehouseTagRepository.insertTag(warehouseId, tagId)
        return findById(warehouseId)
    }

    @Transactional
    fun detachTag(
        warehouseId: UUID,
        tagId: UUID,
    ): WarehouseResponse {
        requireWarehouse(warehouseId)
        warehouseTagRepository.deleteTag(warehouseId, tagId)
        return findById(warehouseId)
    }

    private fun requireWarehouse(id: UUID): Warehouse {
        val w =
            warehouseRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw NoSuchElementException("Warehouse not found: $id")
        w.requireOwnership()
        return w
    }
}

fun Warehouse.toResponse(tags: List<TagDto> = emptyList()) =
    WarehouseResponse(
        id = id!!,
        name = name,
        description = description,
        contactName = contactName,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        contactAddress = contactAddress,
        tags = tags,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WarehouseLocation.toResponse() =
    WarehouseLocationResponse(
        id = id!!,
        warehouseId = warehouseId,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
