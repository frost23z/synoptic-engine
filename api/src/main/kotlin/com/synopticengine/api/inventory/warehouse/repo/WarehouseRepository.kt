package com.synopticengine.api.inventory.warehouse.repo

import com.synopticengine.api.inventory.warehouse.domain.Warehouse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface WarehouseRepository : JpaRepository<Warehouse, UUID> {
    fun findByIdAndDeletedAtIsNull(id: UUID): Warehouse?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Warehouse>

    @Query(
        """
        SELECT w FROM Warehouse w
        WHERE w.deletedAt IS NULL
        AND (LOWER(w.name) LIKE LOWER(CONCAT('%', :q, '%')))
    """,
    )
    fun search(
        q: String,
        pageable: Pageable,
    ): Page<Warehouse>
}
