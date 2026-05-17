package com.synopticengine.api.crm.contact.domain

import com.synopticengine.api.crm.tag.domain.Tag
import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "persons")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class Person :
    AuditableEntity(),
    SoftDeletable {
    @Column
    var organizationId: UUID? = null

    @Column(nullable = false)
    var firstName: String = ""

    @Column(nullable = false)
    var lastName: String = ""

    @Column
    var email: String? = null

    @Column
    var phone: String? = null

    @Column
    var jobTitle: String? = null

    @Column
    override var deletedAt: Instant? = null

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "person_tags",
        joinColumns = [JoinColumn(name = "person_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    val tags: MutableSet<Tag> = mutableSetOf()

    val fullName: String
        get() = "$firstName $lastName".trim()
}
