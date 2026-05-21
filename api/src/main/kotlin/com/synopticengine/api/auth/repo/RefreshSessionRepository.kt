package com.synopticengine.api.auth.repo

import com.synopticengine.api.auth.domain.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RefreshSessionRepository : JpaRepository<RefreshSession, UUID> {
    @Modifying
    @Query(
        """
        UPDATE RefreshSession s
        SET s.revokedAt = :now,
            s.revokedReason = :reason
        WHERE s.familyId = :familyId
          AND s.revokedAt IS NULL
    """,
    )
    fun revokeFamily(
        @Param("familyId") familyId: UUID,
        @Param("now") now: Instant,
        @Param("reason") reason: String,
    ): Int

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :cutoff")
    fun deleteExpiredBefore(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
