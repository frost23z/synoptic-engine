package com.synopticengine.api.inventory.product.repo

import com.synopticengine.api.inventory.product.domain.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProductRepository : JpaRepository<Product, UUID> {
    fun findByIdAndDeletedAtIsNull(id: UUID): Product?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Product>

    fun existsBySkuAndDeletedAtIsNull(sku: String): Boolean

    fun existsBySkuAndIdNotAndDeletedAtIsNull(
        sku: String,
        id: UUID,
    ): Boolean

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.deletedAt IS NULL
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(p.sku)  LIKE LOWER(CONCAT('%', :q, '%')))
    """,
    )
    fun search(
        q: String,
        pageable: Pageable,
    ): Page<Product>
}
