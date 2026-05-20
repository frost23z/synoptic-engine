package com.synopticengine.api.inventory.product.service

import com.synopticengine.api.inventory.InventoryApi
import com.synopticengine.api.inventory.ProductCsvRow
import com.synopticengine.api.inventory.ProductSummary
import com.synopticengine.api.inventory.product.domain.Product
import com.synopticengine.api.inventory.product.repo.ProductRepository
import com.synopticengine.api.inventory.warehouse.repo.WarehouseRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional(readOnly = true)
class InventoryApiImpl(
    private val productRepository: ProductRepository,
    private val warehouseRepository: WarehouseRepository,
) : InventoryApi {
    override fun findProductById(id: UUID): ProductSummary? =
        productRepository.findByIdAndDeletedAtIsNull(id)?.let {
            ProductSummary(id = it.id!!, name = it.name, price = it.price, sku = it.sku, isActive = it.isActive)
        }

    override fun existsProductById(id: UUID): Boolean = productRepository.findByIdAndDeletedAtIsNull(id) != null

    @Transactional
    override fun createProduct(
        name: String,
        description: String?,
        price: BigDecimal,
        sku: String?,
    ): ProductSummary {
        if (sku != null && productRepository.existsBySkuAndDeletedAtIsNull(sku)) {
            throw IllegalStateException("SKU already in use: $sku")
        }
        val product =
            productRepository.save(
                Product().apply {
                    this.name = name
                    this.description = description
                    this.price = price
                    this.sku = sku
                    this.isActive = true
                },
            )
        return ProductSummary(
            id = product.id!!,
            name = product.name,
            price = product.price,
            sku = product.sku,
            isActive = product.isActive,
        )
    }

    override fun findProductOwnerTenant(productId: UUID): UUID? =
        productRepository.findByIdAndDeletedAtIsNull(productId)?.tenantId

    override fun findWarehouseOwnerTenant(warehouseId: UUID): UUID? =
        warehouseRepository.findByIdAndDeletedAtIsNull(warehouseId)?.tenantId

    override fun streamProductsCsv(consume: (ProductCsvRow) -> Unit) {
        var page = 0
        while (true) {
            val pageable = PageRequest.of(page, 1000)
            val result = productRepository.findAllByDeletedAtIsNull(pageable)
            result.content.forEach { product ->
                consume(
                    ProductCsvRow(
                        id = product.id!!,
                        name = product.name,
                        sku = product.sku,
                        price = product.price,
                        description = product.description,
                    ),
                )
            }
            if (!result.hasNext()) break
            page++
        }
    }
}
