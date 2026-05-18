package com.synopticengine.api.crm.contact.domain

import com.synopticengine.api.shared.domain.AuditableEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(name = "organizations")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE organizations SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Organization :
    AuditableEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var name: String = ""

    @Column
    var email: String? = null

    @Column
    var phone: String? = null

    @Column
    var website: String? = null

    @Column(columnDefinition = "TEXT")
    var address: String? = null

    @Column
    override var deletedAt: Instant? = null
}
