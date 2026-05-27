package com.synopticengine.api.inventory.product.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.inventory.product.domain.Product
import com.synopticengine.api.inventory.product.repo.ProductRepository
import com.synopticengine.api.inventory.product.repo.ProductTagRepository
import com.synopticengine.api.inventory.product.web.InventoryEntryResponse
import com.synopticengine.api.inventory.product.web.ProductResponse
import com.synopticengine.api.inventory.warehouse.repo.ProductInventoryRepository
import com.synopticengine.api.inventory.warehouse.repo.WarehouseRepository
import com.synopticengine.api.shared.security.requireOwnership
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
    private val productInventoryRepository: ProductInventoryRepository,
    private val warehouseRepository: WarehouseRepository,
    private val productTagRepository: ProductTagRepository,
    private val crmApi: CrmApi,
) {
    fun findAll(pageable: Pageable): PageResponse<ProductResponse> =
        PageResponse.of(productRepository.findAllByDeletedAtIsNull(pageable)) { it.toResponse(loadTags(it.id!!)) }

    fun findById(id: UUID): ProductResponse {
        val product =
            productRepository.findByIdAndDeletedAtIsNull(id) ?: throw NoSuchElementException("Product not found: $id")
        product.requireOwnership()
        return product.toResponse(loadTags(id))
    }

    fun search(
        q: String,
        pageable: Pageable,
    ): PageResponse<ProductResponse> =
        PageResponse.of(productRepository.search(q, pageable)) { it.toResponse(loadTags(it.id!!)) }

    private fun loadTags(productId: UUID) =
        productTagRepository.findTagIdsByProductId(productId).let { crmApi.findTagsByIds(it) }

    fun getInventory(productId: UUID): List<InventoryEntryResponse> {
        requireProduct(productId)
        return productInventoryRepository.findAllByProductId(productId).map { it.toResponse() }
    }

    @Transactional
    fun create(
        name: String,
        description: String?,
        price: BigDecimal,
        sku: String?,
        isActive: Boolean,
    ): ProductResponse {
        if (sku != null && productRepository.existsBySkuAndDeletedAtIsNull(sku)) {
            throw IllegalStateException("SKU already in use: $sku")
        }
        return productRepository
            .save(
                Product().apply {
                    this.name = name
                    this.description = description
                    this.price = price
                    this.sku = sku
                    this.isActive = isActive
                },
            ).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        description: String?,
        price: BigDecimal,
        sku: String?,
        isActive: Boolean,
    ): ProductResponse {
        val product = requireProduct(id)
        if (sku != null && productRepository.existsBySkuAndIdNotAndDeletedAtIsNull(sku, id)) {
            throw IllegalStateException("SKU already in use: $sku")
        }
        product.name = name
        product.description = description
        product.price = price
        product.sku = sku
        product.isActive = isActive
        return productRepository.save(product).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val product = requireProduct(id)
        product.deletedAt = Instant.now()
        product.isActive = false
        productRepository.save(product)
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            productRepository.findByIdAndDeletedAtIsNull(id)?.let { product ->
                product.deletedAt = Instant.now()
                product.isActive = false
                productRepository.save(product)
            }
        }
    }

    @Transactional
    fun setInventory(
        productId: UUID,
        warehouseId: UUID,
        warehouseLocationId: UUID?,
        quantity: Int,
    ): InventoryEntryResponse {
        requireProduct(productId)
        if (warehouseRepository.findByIdAndDeletedAtIsNull(warehouseId) ==
            null
        ) {
            throw NoSuchElementException("Warehouse not found: $warehouseId")
        }
        val existing =
            productInventoryRepository.findByProductIdAndWarehouseIdAndWarehouseLocationId(
                productId,
                warehouseId,
                warehouseLocationId,
            )
        return if (existing != null) {
            existing.quantity = quantity
            productInventoryRepository.save(existing).toResponse()
        } else {
            productInventoryRepository
                .save(
                    com.synopticengine.api.inventory.warehouse.domain.ProductInventory().apply {
                        this.productId = productId
                        this.warehouseId = warehouseId
                        this.warehouseLocationId = warehouseLocationId
                        this.quantity = quantity
                    },
                ).toResponse()
        }
    }

    @Transactional
    fun attachTag(
        productId: UUID,
        tagId: UUID,
    ): ProductResponse {
        requireProduct(productId)
        if (!crmApi.tagExists(tagId)) throw NoSuchElementException("Tag not found: $tagId")
        productTagRepository.insertTag(productId, tagId)
        return findById(productId)
    }

    @Transactional
    fun detachTag(
        productId: UUID,
        tagId: UUID,
    ): ProductResponse {
        requireProduct(productId)
        productTagRepository.deleteTag(productId, tagId)
        return findById(productId)
    }

    private fun requireProduct(id: UUID): Product {
        val p =
            productRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw NoSuchElementException("Product not found: $id")
        p.requireOwnership()
        return p
    }
}

fun Product.toResponse(tags: List<com.synopticengine.api.crm.TagDto> = emptyList()) =
    ProductResponse(
        id = id!!,
        name = name,
        description = description,
        price = price,
        sku = sku,
        isActive = isActive,
        tags = tags,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun com.synopticengine.api.inventory.warehouse.domain.ProductInventory.toResponse() =
    InventoryEntryResponse(
        id = id!!,
        productId = productId,
        warehouseId = warehouseId,
        warehouseLocationId = warehouseLocationId,
        quantity = quantity,
    )
