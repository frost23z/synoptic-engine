package com.synopticengine.api.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * API keys for programmatic/integration access. Not extending BaseEntity because
 * tenant_id is set explicitly and the table has no soft-delete — revocation is
 * tracked by revokedAt. Keys are looked up by SHA-256(rawKey) so cross-tenant
 * lookup by hash is intentional; tenant ownership is validated after the lookup.
 */
@Entity
@Table(name = "api_keys")
class ApiKey {
    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    var id: UUID = UUID.randomUUID()

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    var tenantId: UUID = UUID.randomUUID()

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    var userId: UUID = UUID.randomUUID()

    @Column(nullable = false, length = 200)
    var name: String = ""

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    var keyHash: String = ""

    @Column(name = "key_prefix", nullable = false, length = 12)
    var keyPrefix: String = ""

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
}
