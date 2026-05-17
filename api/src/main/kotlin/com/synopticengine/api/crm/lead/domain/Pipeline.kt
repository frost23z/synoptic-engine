package com.synopticengine.api.crm.lead.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.time.Instant

@Entity
@Table(name = "pipelines")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Pipeline :
    AuditableEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var name: String = ""

    @Column
    var description: String? = null

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column(nullable = false)
    var isDefault: Boolean = false

    @Column(nullable = false)
    var rottenDays: Int = 30

    @Column
    override var deletedAt: Instant? = null

    @OneToMany(mappedBy = "pipeline", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    val stages: MutableList<Stage> = mutableListOf()
}
