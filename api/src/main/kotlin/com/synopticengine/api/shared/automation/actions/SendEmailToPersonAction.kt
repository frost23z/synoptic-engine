package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.settings.SettingsApi
import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import com.synopticengine.api.shared.email.MailSenderService
import com.synopticengine.api.shared.email.interpolateTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * `send_email_to_person` — render the configured email template and send
 * to the linked person's email address. Action payload:
 *
 * ```
 * { "type": "send_email_to_person", "emailTemplateId": "<uuid>" }
 * ```
 */
@Component
class SendEmailToPersonAction(
    private val settingsApi: SettingsApi,
    private val targetPort: WorkflowTargetPort,
    private val mailSender: MailSenderService,
) : WorkflowAction {
    override val type: String = "send_email_to_person"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        val templateId =
            ctx.action["emailTemplateId"]?.toString()
                ?: throw IllegalArgumentException("send_email_to_person.action.emailTemplateId is required")
        val template =
            settingsApi.findEmailTemplateById(UUID.fromString(templateId))
                ?: throw NoSuchElementException("Email template not found: $templateId")
        val (personId, email) =
            when (ctx.event.entityType) {
                "Person" -> {
                    ctx.event.entityId to targetPort.findPersonEmail(ctx.event.entityId)
                }

                "Lead" -> {
                    targetPort.findLeadPersonAndEmail(ctx.event.entityId)
                        ?: throw NoSuchElementException("Lead or linked person not found: ${ctx.event.entityId}")
                }

                else -> {
                    throw IllegalStateException("send_email_to_person requires a Lead or Person event")
                }
            }
        val to = email ?: throw IllegalStateException("Person $personId has no email address")
        val renderContext =
            buildMap {
                put("eventName", ctx.event.eventName)
                put("entityType", ctx.event.entityType)
                put("entityId", ctx.event.entityId.toString())
                put("personId", personId.toString())
                put("personEmail", to)
                ctx.event.payload.forEach { (key, value) ->
                    if (value != null) put(key, value.toString())
                }
            }
        mailSender.sendHtmlEmail(
            to,
            interpolateTemplate(template.subject, renderContext),
            interpolateTemplate(template.content, renderContext),
        )
        return mapOf("personId" to personId, "templateId" to templateId, "to" to to)
    }
}
