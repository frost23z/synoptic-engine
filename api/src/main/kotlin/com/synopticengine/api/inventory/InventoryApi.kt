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

    /** Streaming export of every product as CSV rows. See CrmApi.streamPersonsCsv for the rationale. */
    fun streamProductsCsv(consume: (ProductCsvRow) -> Unit)

    /** Owner of a given record — needed by sharing.service.RecordShareService. */
    fun findProductOwnerTenant(productId: UUID): UUID?

    fun findWarehouseOwnerTenant(warehouseId: UUID): UUID?
}
