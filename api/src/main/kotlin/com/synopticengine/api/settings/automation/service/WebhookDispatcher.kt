package com.synopticengine.api.settings.automation.service

import com.synopticengine.api.settings.automation.domain.WebhookDeliveryRun
import com.synopticengine.api.settings.automation.domain.WebhookDeliveryRunStatus
import com.synopticengine.api.settings.automation.repo.WebhookDeliveryRunRepository
import com.synopticengine.api.shared.DomainEvent
import com.synopticengine.api.shared.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class WebhookDispatcher(
    private val webhookRepository: com.synopticengine.api.settings.automation.repo.WebhookRepository,
    private val deliveryRunRepository: WebhookDeliveryRunRepository,
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(WebhookDispatcher::class.java)

    @EventListener
    @Async
    fun onDomainEvent(event: DomainEvent) {
        // P1-1: relies on TenantPropagatingTaskDecorator carrying the publisher's
        // TenantContext into this @Async thread. If a future caller fires the
        // event outside a tenant context, the webhook lookup would return rows
        // from every tenant — refuse to dispatch rather than fan out blindly.
        if (TenantContext.get() == null) {
            log.warn("Skipping webhook dispatch for ${event.eventName}: no TenantContext on the listener thread")
            return
        }

        val matching =
            webhookRepository.findByIsActiveTrue().filter { webhook ->
                webhook.events.isEmpty() || event.eventName in webhook.events
            }
        if (matching.isEmpty()) return

        val payload =
            mapOf(
                "event" to event.eventName,
                "entityType" to event.entityType,
                "entityId" to event.entityId.toString(),
                "payload" to event.payload,
            )

        for (webhook in matching) {
            try {
                val response =
                    restClient
                        .post()
                        .uri(webhook.payloadUrl)
                        .body(payload)
                        .retrieve()
                        .toEntity(String::class.java)
                recordSuccess(webhook.id!!, event, response.statusCode.value(), response.body)
                log.debug("Webhook ${webhook.id} fired for event ${event.eventName}")
            } catch (e: RestClientResponseException) {
                recordFailure(webhook.id!!, event, e.statusCode.value(), e.responseBodyAsString, e.message)
                log.warn(
                    "Webhook ${webhook.id} failed for event ${event.eventName}: HTTP ${e.statusCode}",
                )
            } catch (e: Exception) {
                recordFailure(webhook.id!!, event, null, null, e.message)
                log.warn("Webhook ${webhook.id} failed for event ${event.eventName}: ${e.message}")
            }
        }
    }

    private fun recordSuccess(
        webhookId: java.util.UUID,
        event: DomainEvent,
        statusCode: Int,
        body: String?,
    ) {
        deliveryRunRepository.save(
            WebhookDeliveryRun().apply {
                this.webhookId = webhookId
                this.eventName = event.eventName
                this.entityType = event.entityType
                this.entityId = event.entityId
                this.status = WebhookDeliveryRunStatus.SUCCESS
                this.responseCode = statusCode
                this.responseBody = body?.take(2_000) // bound the row size
            },
        )
    }

    private fun recordFailure(
        webhookId: java.util.UUID,
        event: DomainEvent,
        statusCode: Int?,
        body: String?,
        error: String?,
    ) {
        deliveryRunRepository.save(
            WebhookDeliveryRun().apply {
                this.webhookId = webhookId
                this.eventName = event.eventName
                this.entityType = event.entityType
                this.entityId = event.entityId
                this.status = WebhookDeliveryRunStatus.FAILED
                this.responseCode = statusCode
                this.responseBody = body?.take(2_000)
                this.errorMessage = error?.take(2_000)
            },
        )
    }
}
