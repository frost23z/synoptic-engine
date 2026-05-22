package com.synopticengine.api.crm.lead.domain

import com.synopticengine.api.crm.email.domain.Email
import com.synopticengine.api.crm.tag.domain.Tag
import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "leads")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
// Cross-tenant visibility is implemented in the service layer (LeadService.findAll
// merges own-tenant + sharedResourceFinder.idsFor(LEADS)). The Hibernate filter
// remains tenant-strict to avoid alias/JOIN-FETCH ambiguity; RLS (V040) is the
// authoritative trust boundary against native queries that bypass Hibernate.
@SQLDelete(sql = "UPDATE leads SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Lead :
    AuditableEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var title: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(precision = 15, scale = 2)
    var amount: BigDecimal? = null

    @Column
    var expectedCloseDate: LocalDate? = null

    @Convert(converter = LeadStatusConverter::class)
    @Column(nullable = false)
    var status: LeadStatus = LeadStatus.OPEN

    @Column(columnDefinition = "TEXT")
    var lostReason: String? = null

    @Column
    var closedAt: Instant? = null

    @Column(nullable = false)
    var pipelineId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")

    @Column(nullable = false)
    var stageId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")

    @Column(nullable = false)
    var stageUpdatedAt: Instant = Instant.now()

    @Column
    var personId: UUID? = null

    @Column
    var organizationId: UUID? = null

    @Column
    var leadSourceId: UUID? = null

    @Column
    var leadTypeId: UUID? = null

    @Column
    var userId: UUID? = null

    @Column
    override var deletedAt: Instant? = null

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "lead_tags",
        joinColumns = [JoinColumn(name = "lead_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    val tags: MutableSet<Tag> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "lead_emails",
        joinColumns = [JoinColumn(name = "lead_id")],
        inverseJoinColumns = [JoinColumn(name = "email_id")],
    )
    val emails: MutableSet<Email> = mutableSetOf()
}
