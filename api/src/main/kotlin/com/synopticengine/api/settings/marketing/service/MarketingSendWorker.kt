package com.synopticengine.api.settings.marketing.service

import com.synopticengine.api.settings.marketing.domain.SendJobStatus
import com.synopticengine.api.settings.marketing.repo.MarketingSendJobRepository
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.email.MailSenderService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class MarketingSendWorker(
    private val jdbcTemplate: JdbcTemplate,
    private val sendJobRepository: MarketingSendJobRepository,
    private val mailSenderService: MailSenderService,
    @Value("\${synoptic.marketing.send-batch-size:100}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_ATTEMPTS = 5
        private val MAX_BACKOFF: Duration = Duration.ofHours(24)
    }

    @Scheduled(
        fixedDelayString = "\${synoptic.marketing.send-fixed-delay-ms:30000}",
        initialDelayString = "\${synoptic.marketing.send-initial-delay-ms:60000}",
    )
    fun processBatch() {
        val now = Instant.now()
        val rows =
            jdbcTemplate.queryForList(
                "SELECT id, tenant_id FROM marketing_send_jobs WHERE status IN ('PENDING','FAILED') AND attempt_count < ? AND next_attempt_at <= ? ORDER BY next_attempt_at ASC LIMIT ?",
                MAX_ATTEMPTS,
                Timestamp.from(now),
                batchSize,
            )
        rows.forEach { row ->
            val jobId = row["id"] as UUID
            val tenantId = row["tenant_id"] as UUID
            TenantContext.runAs(tenantId) { processJob(jobId) }
        }
    }

    @Transactional
    private fun processJob(jobId: UUID) {
        val job = sendJobRepository.findById(jobId).orElse(null) ?: return
        try {
            mailSenderService.sendHtmlEmail(job.recipient, job.subject, job.body)
            job.status = SendJobStatus.SENT
            job.sentAt = Instant.now()
        } catch (ex: Exception) {
            log.warn("Failed to send marketing job $jobId attempt=${job.attemptCount + 1}", ex)
            job.attemptCount += 1
            job.errorMessage = ex.message?.take(1000)
            if (job.attemptCount >= MAX_ATTEMPTS) {
                job.status = SendJobStatus.FAILED
                job.failedAt = Instant.now()
            } else {
                job.status = SendJobStatus.FAILED
                job.nextAttemptAt = Instant.now().plus(backoffFor(job.attemptCount))
            }
        }
        sendJobRepository.save(job)
    }

    private fun backoffFor(attempt: Int): Duration {
        val computed = Duration.ofMinutes(2L shl (attempt - 1))
        return if (computed > MAX_BACKOFF) MAX_BACKOFF else computed
    }
}
