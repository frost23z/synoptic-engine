package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.LeadProduct
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface LeadProductWithProductInfo {
    val productId: UUID
    val name: String
    val sku: String?
    val quantity: Int
    val unitPrice: BigDecimal?
    val productPrice: BigDecimal
}

interface LeadProductRepository : JpaRepository<LeadProduct, UUID> {
    fun findAllByLeadId(leadId: UUID): List<LeadProduct>

    fun findByLeadIdAndProductId(
        leadId: UUID,
        productId: UUID,
    ): LeadProduct?

    @Query(
        """
        SELECT
            lp.product_id   AS productId,
            COALESCE(p.name, 'Unknown') AS name,
            p.sku           AS sku,
            lp.quantity     AS quantity,
            lp.unit_price   AS unitPrice,
            COALESCE(p.price, 0) AS productPrice
        FROM lead_products lp
        LEFT JOIN products p ON p.id = lp.product_id AND p.deleted_at IS NULL
        WHERE lp.lead_id = :leadId
        """,
        nativeQuery = true,
    )
    fun findAllWithProductInfoByLeadId(leadId: UUID): List<LeadProductWithProductInfo>

    @Modifying
    @Query("DELETE FROM LeadProduct lp WHERE lp.leadId = :leadId AND lp.productId = :productId")
    fun deleteByLeadIdAndProductId(
        leadId: UUID,
        productId: UUID,
    )
}
