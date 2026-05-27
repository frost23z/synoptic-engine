package com.synopticengine.api.inventory.transfer.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "transfer_orders")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE transfer_orders SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class TransferOrder :
    AuditableEntity(),
    SoftDeletable {
    @Column(name = "from_location_id", nullable = false)
    var fromLocationId: UUID = UUID.randomUUID()

    @Column(name = "to_location_id", nullable = false)
    var toLocationId: UUID = UUID.randomUUID()

    @Column(name = "product_id", nullable = false)
    var productId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var quantity: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TransferStatus = TransferStatus.PENDING

    @Column(name = "out_movement_id")
    var outMovementId: UUID? = null

    @Column(name = "in_movement_id")
    var inMovementId: UUID? = null

    @Column(columnDefinition = "TEXT")
    var notes: String? = null

    @Column
    override var deletedAt: Instant? = null
}
