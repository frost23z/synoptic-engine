package com.synopticengine.api.inventory.movement.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.math.BigDecimal
import java.util.UUID

/**
 * Append-only ledger entry — never mutate after save.
 *
 * Cost method: weighted-average (WAC). On RECEIPT, the cached unit_cost in
 * product_inventories is updated as:
 *   (existing_on_hand × existing_unit_cost + qty × receipt_unit_cost) / (existing_on_hand + qty)
 * WAC is used for ISSUE/ADJUST cost valuation.
 */
@Entity
@Table(name = "inventory_movements")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class InventoryMovement : BaseEntity() {
    @Column(nullable = false)
    var productId: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    var movementType: MovementType = MovementType.ADJUST

    @Column(name = "from_location_id")
    var fromLocationId: UUID? = null

    @Column(name = "to_location_id")
    var toLocationId: UUID? = null

    @Column(nullable = false)
    var quantity: Int = 0

    @Column(precision = 15, scale = 4)
    var unitCost: BigDecimal? = null

    @Column(name = "ref_doc_type", length = 50)
    var refDocType: String? = null

    @Column(name = "ref_doc_id")
    var refDocId: UUID? = null

    @Column(name = "actor_id")
    var actorId: UUID? = null

    @Column(columnDefinition = "TEXT")
    var notes: String? = null
}
