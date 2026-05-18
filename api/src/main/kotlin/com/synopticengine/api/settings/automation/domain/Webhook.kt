package com.synopticengine.api.settings.automation.domain

import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "webhooks")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE webhooks SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Webhook :
    BaseEntity(),
    SoftDeletable {
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "payload_url", nullable = false, length = 2048)
    var payloadUrl: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var events: List<String> = emptyList()

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    override var deletedAt: Instant? = null
}
