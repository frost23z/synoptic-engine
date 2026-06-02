package com.synopticengine.api.auth.repo

import com.synopticengine.api.auth.domain.LoginHistory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LoginHistoryRepository : JpaRepository<LoginHistory, UUID> {
    @Query("SELECT h FROM LoginHistory h WHERE h.userId = :userId ORDER BY h.loggedInAt DESC")
    fun findRecentByUserId(
        @Param("userId") userId: UUID,
        pageable: Pageable,
    ): List<LoginHistory>
}
