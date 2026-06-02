package com.synopticengine.api.auth.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.time.Instant
import java.util.UUID

/**
 * One-time backup code for MFA recovery.
 * [codeHash] stores SHA-256 of the plaintext code; the plaintext is shown once at setup.
 * [usedAt] is set when the code is consumed — used codes are never deleted so they
 * cannot be replayed.
 */
@Entity
@Table(name = "mfa_backup_codes")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class MfaBackupCode : BaseEntity() {
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    var userId: UUID = UUID.randomUUID()

    @Column(name = "code_hash", nullable = false, length = 255)
    var codeHash: String = ""

    @Column(name = "used_at")
    var usedAt: Instant? = null
}
