package com.synopticengine.api.settings.automation.repo

import com.synopticengine.api.settings.automation.domain.Webhook
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WebhookRepository : JpaRepository<Webhook, UUID> {
    fun findByIsActiveTrue(): List<Webhook>

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT w FROM Webhook w WHERE w.id = :id AND w.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Webhook?
}
