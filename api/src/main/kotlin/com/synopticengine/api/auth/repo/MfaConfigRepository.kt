package com.synopticengine.api.auth.repo

import com.synopticengine.api.auth.domain.MfaConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MfaConfigRepository : JpaRepository<MfaConfig, UUID> {
    @Query("SELECT m FROM MfaConfig m WHERE m.userId = :userId AND m.deletedAt IS NULL")
    fun findActiveByUserId(
        @Param("userId") userId: UUID,
    ): MfaConfig?
}
