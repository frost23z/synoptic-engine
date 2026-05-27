package com.synopticengine.api.settings.marketing.repo

import com.synopticengine.api.settings.marketing.domain.MarketingSendJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface MarketingSendJobRepository : JpaRepository<MarketingSendJob, UUID> {
    @Query(
        value = """
            SELECT * FROM marketing_send_jobs
            WHERE status IN ('PENDING','FAILED')
              AND attempt_count < :maxAttempts
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at ASC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findPendingForRetry(
        @Param("now") now: Instant,
        @Param("limit") limit: Int,
        @Param("maxAttempts") maxAttempts: Int,
    ): List<MarketingSendJob>
}
