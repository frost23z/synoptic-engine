package com.synopticengine.api.sharing.service

import com.synopticengine.api.sharing.repo.RecordShareRepository
import com.synopticengine.api.sharing.repo.ResourceVisibilityRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Daily cleanup of expired sharing rows.
 *
 * Why this exists: `record_shares.expires_at` and `resource_visibility.expires_at`
 * are filtered at *read* time (the access-level computation in
 * [ResourceVisibilityService.effectiveAccess] and the SQL `app_has_visibility()`
 * function both reject `expires_at <= NOW()`), but the rows themselves are
 * never deleted. Without this job both tables would grow unbounded for any
 * tenant that uses time-limited shares (the common case for project-style
 * collaboration), and the partial expiry index degrades into uselessness.
 *
 * Cutoff is `now() − grace`. A 7-day grace gives operators a window to
 * investigate a complaint ("the share I had access to yesterday isn't
 * working any more") by joining against the still-present row before it's
 * pruned. Bump the property below if you need longer.
 *
 * Schedule: 03:00 UTC each day. Cron from properties so an operator can shift
 * to a quieter window per-deployment without a code change.
 */
@Component
class ShareExpiryCleanupWorker(
    private val recordShareRepository: RecordShareRepository,
    private val resourceVisibilityRepository: ResourceVisibilityRepository,
    @Value("\${synoptic.sharing.expiry-grace-days:7}") private val graceDays: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${synoptic.sharing.expiry-cleanup-cron:0 0 3 * * *}")
    @Transactional
    fun cleanupExpired() {
        val cutoff = Instant.now().minus(Duration.ofDays(graceDays))
        val visibilityCount = resourceVisibilityRepository.deleteExpiredBefore(cutoff)
        val shareCount = recordShareRepository.deleteExpiredBefore(cutoff)
        if (visibilityCount > 0 || shareCount > 0) {
            log.info(
                "Sharing expiry cleanup removed $visibilityCount visibility row(s) and $shareCount record_share row(s) older than $cutoff",
            )
        }
    }
}
