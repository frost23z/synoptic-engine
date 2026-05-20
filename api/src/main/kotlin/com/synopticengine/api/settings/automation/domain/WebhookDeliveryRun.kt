package com.synopticengine.api.settings.automation.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

/**
 * 09 P3-1 — durable record of webhook dispatch attempts. The runtime equivalent of
 * `WorkflowActionRun` for the webhook subsystem: lets operators reconstruct a
 * subscriber's view of which events they should have received.
 */
@Entity
@Table(name = "webhook_delivery_runs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class WebhookDeliveryRun : BaseEntity() {
    @Column(nullable = false)
    var webhookId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var eventName: String = ""

    @Column(nullable = false)
    var entityType: String = ""

    @Column(nullable = false)
    var entityId: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WebhookDeliveryRunStatus = WebhookDeliveryRunStatus.SUCCESS

    @Column
    var responseCode: Int? = null

    @Column(columnDefinition = "TEXT")
    var responseBody: String? = null

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null
}

enum class WebhookDeliveryRunStatus { SUCCESS, FAILED }
