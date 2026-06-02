package com.synopticengine.api.auth.repo

import com.synopticengine.api.auth.domain.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    @Query("""
        SELECT k FROM ApiKey k
        WHERE k.keyHash = :hash
          AND k.revokedAt IS NULL
          AND (k.expiresAt IS NULL OR k.expiresAt > :now)
    """)
    fun findActiveByHash(
        @Param("hash") hash: String,
        @Param("now") now: Instant,
    ): ApiKey?

    @Query("""
        SELECT k FROM ApiKey k
        WHERE k.tenantId = :tenantId
          AND k.userId = :userId
          AND k.revokedAt IS NULL
        ORDER BY k.createdAt DESC
    """)
    fun findActiveByTenantAndUser(
        @Param("tenantId") tenantId: UUID,
        @Param("userId") userId: UUID,
    ): List<ApiKey>
}
