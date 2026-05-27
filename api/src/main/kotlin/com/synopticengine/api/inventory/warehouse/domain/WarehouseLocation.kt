package com.synopticengine.api.inventory.warehouse.domain

import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "warehouse_locations")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE warehouse_locations SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class WarehouseLocation :
    BaseEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var warehouseId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var name: String = ""

    @Column
    override var deletedAt: Instant? = null
}
