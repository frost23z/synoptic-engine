package com.synopticengine.api.shared.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@Component
class EventPublicationRetentionWorker(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${synoptic.modulith.event-retention.completed-days:30}") private val completedDays: Long,
    @Value("\${synoptic.modulith.event-retention.archive-days:180}") private val archiveDays: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${synoptic.modulith.event-retention.cron:0 30 3 * * *}")
    @Transactional
    fun cleanup() {
        val now = Instant.now()
        val completedCutoff = now.minus(Duration.ofDays(completedDays))
        val archiveCutoff = now.minus(Duration.ofDays(archiveDays))

        val archived =
            jdbcTemplate.update(
                """
                INSERT INTO event_publication_archive (
                    id, listener_id, event_type, serialized_event,
                    publication_date, completion_date, status,
                    completion_attempts, last_resubmission_date
                )
                SELECT
                    id, listener_id, event_type, serialized_event,
                    publication_date, completion_date, status,
                    completion_attempts, last_resubmission_date
                FROM event_publication
                WHERE completion_date IS NOT NULL
                  AND completion_date < ?
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                Timestamp.from(completedCutoff),
            )

        val prunedLive =
            jdbcTemplate.update(
                """
                DELETE FROM event_publication
                WHERE completion_date IS NOT NULL
                  AND completion_date < ?
                """.trimIndent(),
                Timestamp.from(completedCutoff),
            )

        val prunedArchive =
            jdbcTemplate.update(
                """
                DELETE FROM event_publication_archive
                WHERE completion_date IS NOT NULL
                  AND completion_date < ?
                """.trimIndent(),
                Timestamp.from(archiveCutoff),
            )

        if (archived > 0 || prunedLive > 0 || prunedArchive > 0) {
            log.info(
                "Event publication retention archived $archived, pruned live $prunedLive, pruned archive $prunedArchive",
            )
        }
    }
}
