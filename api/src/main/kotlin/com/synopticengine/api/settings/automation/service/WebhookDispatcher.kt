package com.synopticengine.api.settings.automation.service

import com.synopticengine.api.settings.automation.domain.WebhookDeliveryRun
import com.synopticengine.api.settings.automation.domain.WebhookDeliveryRunStatus
import com.synopticengine.api.settings.automation.repo.WebhookDeliveryRunRepository
import com.synopticengine.api.shared.DomainEvent
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.security.OutboundUrlValidator
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class WebhookDispatcher(
    private val webhookRepository: com.synopticengine.api.settings.automation.repo.WebhookRepository,
    private val deliveryRunRepository: WebhookDeliveryRunRepository,
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val outboundUrlValidator: OutboundUrlValidator,
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
            // T1.2 — re-validate at send time. The URL was validated at save time
            // (in AutomationService), but re-validate here as defense-in-depth:
            // a stored URL that passed validation when DNS had a public address
            // could later resolve to a private range (DNS rebinding), or an admin
            // bypass could have stored an invalid URL.
            try {
                outboundUrlValidator.validate(webhook.payloadUrl)
            } catch (e: IllegalArgumentException) {
                log.warn("Webhook ${webhook.id} skipped: URL validation failed — ${e.message}")
                recordFailure(webhook.id!!, event, null, null, "URL validation failed: ${e.message}")
                continue
            }

            try {
                val payloadJson = objectMapper.writeValueAsString(payload)
                val response =
                    restClient
                        .post()
                        .uri(webhook.payloadUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .let { req ->
                            webhook.secret?.takeIf { it.isNotBlank() }?.let { secret ->
                                req.header("X-Synoptic-Signature", signPayload(secret, payloadJson))
                            } ?: req
                        }.body(payloadJson)
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

    private fun signPayload(
        secret: String,
        payloadJson: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payloadJson.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
