package com.synopticengine.api.inventory.product.repo

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ProductTagRepository(
    private val jdbc: JdbcTemplate,
) {
    fun findTagIdsByProductId(productId: UUID): List<UUID> =
        jdbc
            .queryForList(
                "SELECT tag_id FROM product_tags WHERE product_id = ?",
                UUID::class.java,
                productId,
            ).filterNotNull()

    fun insertTag(
        productId: UUID,
        tagId: UUID,
    ) {
        jdbc.update(
            "INSERT INTO product_tags (product_id, tag_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            productId,
            tagId,
        )
    }

    fun deleteTag(
        productId: UUID,
        tagId: UUID,
    ) {
        jdbc.update("DELETE FROM product_tags WHERE product_id = ? AND tag_id = ?", productId, tagId)
    }
}
