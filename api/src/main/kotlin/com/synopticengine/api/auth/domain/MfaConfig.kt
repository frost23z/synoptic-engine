package com.synopticengine.api.auth.domain

import com.synopticengine.api.shared.crypto.AesGcmEncryptionConverter
import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.time.Instant
import java.util.UUID

/**
 * Stores the TOTP secret for a user's MFA configuration.
 * One active row per user — enforced by a partial unique index on user_id WHERE deleted_at IS NULL.
 * Extends BaseEntity so tenant_id + audit columns are managed by the framework.
 * Secret is AES-GCM encrypted at rest via [AesGcmEncryptionConverter].
 */
@Entity
@Table(name = "user_mfa_configs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class MfaConfig : BaseEntity() {
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    var userId: UUID = UUID.randomUUID()

    @Column(name = "totp_secret", nullable = false, columnDefinition = "text")
    @Convert(converter = AesGcmEncryptionConverter::class)
    var totpSecret: String = ""

    @Column(nullable = false)
    var enabled: Boolean = false

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
}
