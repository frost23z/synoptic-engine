package com.synopticengine.api.inventory

import java.math.BigDecimal
import java.util.UUID

data class ProductSummary(
    val id: UUID,
    val name: String,
    val price: BigDecimal,
    val sku: String?,
    val isActive: Boolean,
)

data class ProductCsvRow(
    val id: UUID,
    val name: String,
    val sku: String?,
    val price: BigDecimal,
    val description: String?,
)

interface InventoryApi {
    fun findProductById(id: UUID): ProductSummary?

    fun existsProductById(id: UUID): Boolean

    fun createProduct(
        name: String,
        description: String?,
        price: BigDecimal,
        sku: String?,
    ): ProductSummary

    fun exportProductsCsv(): List<ProductCsvRow>
}
