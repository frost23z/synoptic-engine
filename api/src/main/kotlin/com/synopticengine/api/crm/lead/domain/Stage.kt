package com.synopticengine.api.crm.lead.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "stages")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE stages SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Stage :
    AuditableEntity(),
    SoftDeletable {
    @Column(name = "pipeline_id", insertable = false, updatable = false)
    var pipelineId: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    lateinit var pipeline: Pipeline

    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var sortOrder: Int = 0

    @Column
    var color: String? = null

    @Column(nullable = false)
    var probability: Int = 0

    /** null = regular stage, "won" = closed-won, "lost" = closed-lost */
    @Column
    var code: String? = null

    @Column
    override var deletedAt: Instant? = null
}
