package com.synopticengine.api.inventory.warehouse.repo

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class WarehouseTagRepository(
    private val jdbc: JdbcTemplate,
) {
    fun findTagIdsByWarehouseId(warehouseId: UUID): List<UUID> =
        jdbc
            .queryForList(
                "SELECT tag_id FROM warehouse_tags WHERE warehouse_id = ?",
                UUID::class.java,
                warehouseId,
            ).filterNotNull()

    fun insertTag(
        warehouseId: UUID,
        tagId: UUID,
    ) {
        jdbc.update(
            "INSERT INTO warehouse_tags (warehouse_id, tag_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            warehouseId,
            tagId,
        )
    }

    fun deleteTag(
        warehouseId: UUID,
        tagId: UUID,
    ) {
        jdbc.update("DELETE FROM warehouse_tags WHERE warehouse_id = ? AND tag_id = ?", warehouseId, tagId)
    }
}
