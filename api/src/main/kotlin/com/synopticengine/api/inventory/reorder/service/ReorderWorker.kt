package com.synopticengine.api.inventory.reorder.service

import com.synopticengine.api.shared.DomainEvent
import com.synopticengine.api.shared.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReorderWorker(
    private val jdbcTemplate: JdbcTemplate,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${synoptic.inventory.reorder.cron:0 0 6 * * *}") private val cron: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${synoptic.inventory.reorder.cron:0 0 6 * * *}")
    fun checkReorderPoints() {
        val rows =
            jdbcTemplate.queryForList(
                """
                SELECT p.id AS product_id, p.tenant_id, p.reorder_threshold,
                       COALESCE(SUM(pi.on_hand), 0) AS total_on_hand
                FROM products p
                LEFT JOIN product_inventories pi ON pi.product_id = p.id
                WHERE p.deleted_at IS NULL
                  AND p.reorder_threshold IS NOT NULL
                GROUP BY p.id, p.tenant_id, p.reorder_threshold
                HAVING COALESCE(SUM(pi.on_hand), 0) <= p.reorder_threshold
                """.trimIndent(),
            )
        rows.forEach { row ->
            val productId = row["product_id"] as UUID
            val tenantId = row["tenant_id"] as UUID
            val threshold = row["reorder_threshold"] as Int
            val onHand = (row["total_on_hand"] as Number).toInt()
            TenantContext.runAs(tenantId) {
                eventPublisher.publishEvent(
                    DomainEvent(
                        eventName = "inventory.low-stock",
                        entityType = "Product",
                        entityId = productId,
                        payload = mapOf("tenantId" to tenantId.toString(), "onHand" to onHand, "reorderThreshold" to threshold),
                    ),
                )
            }
            log.info("Low-stock event for product=$productId tenant=$tenantId onHand=$onHand threshold=$threshold")
        }
    }
}
