package com.synopticengine.api.auth.repo

import com.synopticengine.api.auth.domain.MfaBackupCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MfaBackupCodeRepository : JpaRepository<MfaBackupCode, UUID> {
    @Query("SELECT c FROM MfaBackupCode c WHERE c.userId = :userId AND c.usedAt IS NULL")
    fun findUnusedByUserId(
        @Param("userId") userId: UUID,
    ): List<MfaBackupCode>

    @Modifying
    @Query("DELETE FROM MfaBackupCode c WHERE c.userId = :userId")
    fun deleteAllByUserId(
        @Param("userId") userId: UUID,
    )
}
