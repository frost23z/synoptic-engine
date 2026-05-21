package com.synopticengine.api.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_refresh_sessions")
class RefreshSession {
    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    var id: UUID = UUID.randomUUID()

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    var userId: UUID = UUID.randomUUID()

    @Column(name = "family_id", nullable = false, columnDefinition = "uuid")
    var familyId: UUID = UUID.randomUUID()

    @Column(name = "parent_session_id", columnDefinition = "uuid")
    var parentSessionId: UUID? = null

    @Column(name = "token_hash", nullable = false, length = 128)
    var tokenHash: String = ""

    @Column(name = "issued_at", nullable = false)
    var issuedAt: Instant = Instant.now()

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now()

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "revoked_reason", length = 120)
    var revokedReason: String? = null

    @Column(name = "replaced_by_session_id", columnDefinition = "uuid")
    var replacedBySessionId: UUID? = null

    fun isExpired(now: Instant = Instant.now()): Boolean = !expiresAt.isAfter(now)
}
