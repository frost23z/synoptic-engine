package com.synopticengine.api.settings.automation.repo

import com.synopticengine.api.settings.automation.domain.WebhookDeliveryRun
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebhookDeliveryRunRepository : JpaRepository<WebhookDeliveryRun, UUID> {
    fun findAllByWebhookIdOrderByCreatedAtDesc(
        webhookId: UUID,
        pageable: Pageable,
    ): Page<WebhookDeliveryRun>
}
