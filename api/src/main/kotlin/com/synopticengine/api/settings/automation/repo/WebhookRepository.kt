package com.synopticengine.api.settings.automation.repo

import com.synopticengine.api.settings.automation.domain.Webhook
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebhookRepository : JpaRepository<Webhook, UUID> {
    fun findByIsActiveTrue(): List<Webhook>
}
