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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "persons")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE persons SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Person :
    AuditableEntity(),
    SoftDeletable {
    @Column
    var organizationId: UUID? = null

    @Column(nullable = false)
    var firstName: String = ""

    @Column(nullable = false)
    var lastName: String = ""

    /** Legacy scalar; kept transitionally until the read path is fully on [emails]. */
    @Column
    var email: String? = null

    /** Legacy scalar; kept transitionally until the read path is fully on [contactNumbers]. */
    @Column
    var phone: String? = null

    /** JSON-encoded `List<ContactEntry>`. Always populated; defaults to `"[]"`. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var emails: String = "[]"

    /** JSON-encoded `List<ContactEntry>`. Always populated; defaults to `"[]"`. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_numbers", columnDefinition = "jsonb", nullable = false)
    var contactNumbers: String = "[]"

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
