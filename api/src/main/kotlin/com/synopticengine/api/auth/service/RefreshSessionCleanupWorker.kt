package com.synopticengine.api.auth.service

import com.synopticengine.api.auth.repo.RefreshSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
class RefreshSessionCleanupWorker(
    private val refreshSessionRepository: RefreshSessionRepository,
    @Value("\${synoptic.auth.refresh.cleanup-retention-days:14}") private val retentionDays: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${synoptic.auth.refresh.cleanup-cron:0 15 3 * * *}")
    @Transactional
    fun cleanupExpiredSessions() {
        val cutoff = Instant.now().minus(Duration.ofDays(retentionDays))
        val deleted = refreshSessionRepository.deleteExpiredBefore(cutoff)
        if (deleted > 0) {
            log.info("Refresh-session cleanup removed $deleted row(s) older than $cutoff")
        }
    }
}
