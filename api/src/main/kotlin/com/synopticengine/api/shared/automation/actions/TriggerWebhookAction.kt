package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.settings.SettingsApi
import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * `trigger_webhook` — fire a specific webhook by id, with the event payload
 * as the body. Unlike the broadcast `WebhookDispatcher` in `settings`,
 * this is an opt-in trigger from a workflow.
 */
@Component
class TriggerWebhookAction(
    private val settingsApi: SettingsApi,
    private val restClient: RestClient,
) : WorkflowAction {
    override val type: String = "trigger_webhook"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        val webhookIdRaw =
            ctx.action["webhookId"]?.toString()
                ?: throw IllegalArgumentException("trigger_webhook.action.webhookId is required")
        val webhookId = UUID.fromString(webhookIdRaw)
        val webhook =
            settingsApi.findWebhookById(webhookId)
                ?: throw NoSuchElementException("Webhook not found: $webhookId")
        if (!webhook.isActive) {
            return mapOf("webhookId" to webhookId, "skipped" to true)
        }
        val body =
            mapOf(
                "event" to ctx.event.eventName,
                "entityType" to ctx.event.entityType,
                "entityId" to ctx.event.entityId.toString(),
                "payload" to ctx.event.payload,
                "source" to "workflow",
            )
        restClient
            .post()
            .uri(webhook.payloadUrl)
            .body(body)
            .retrieve()
            .toBodilessEntity()
        return mapOf("webhookId" to webhookId, "url" to webhook.payloadUrl)
    }
}
