package com.synopticengine.api.settings.attribute.domain

import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(name = "attributes")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE attributes SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Attribute :
    BaseEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var code: String = ""

    @Column(nullable = false)
    var adminName: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AttributeType = AttributeType.TEXT

    @Column(nullable = false)
    var isUserDefined: Boolean = true

    @Column
    var lookup: String? = null

    @Column(nullable = false)
    var entityType: String = ""

    @Column(nullable = false)
    var sortOrder: Int = 0

    @Column
    override var deletedAt: Instant? = null

    @OneToMany(mappedBy = "attribute", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    val options: MutableList<AttributeOption> = mutableListOf()
}
