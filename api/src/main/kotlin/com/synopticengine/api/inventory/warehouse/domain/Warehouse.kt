package com.synopticengine.api.inventory.warehouse.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.time.Instant

@Entity
@Table(name = "warehouses")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Warehouse :
    AuditableEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column
    var contactName: String? = null

    @Column
    var contactEmail: String? = null

    @Column
    var contactPhone: String? = null

    @Column(columnDefinition = "TEXT")
    var contactAddress: String? = null

    @Column
    override var deletedAt: Instant? = null
}
