package com.synopticengine.api.settings.automation.domain

import com.synopticengine.api.shared.crypto.AesGcmEncryptionConverter
import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Convert
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

    /**
     * HMAC signing secret for outbound webhook deliveries.
     *
     * Stored encrypted at rest via [AesGcmEncryptionConverter] (T2.4). The value
     * exposed in API responses is masked (see WebhookService / SettingsApiImpl) so
     * callers can confirm a secret is set without reading it back. The converter
     * tolerates existing plaintext rows (reads them through transparently) and
     * encrypts on the next write — see [AesGcmEncryptionConverter] for the rollout
     * procedure.
     */
    @Column(name = "secret", columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter::class)
    var secret: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var events: List<String> = emptyList()

    @Column(nullable = false)
    var isActive: Boolean = true

    @Column
    override var deletedAt: Instant? = null
}
