package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.identity.IdentityApi
import com.synopticengine.api.settings.SettingsApi
import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import com.synopticengine.api.shared.email.MailSenderService
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * `send_email_to_sales_owner` — sends the template to the assigned salesperson
 * (Lead.userId). Errors out cleanly if the lead has no owner.
 */
@Component
class SendEmailToSalesOwnerAction(
    private val settingsApi: SettingsApi,
    private val targetPort: WorkflowTargetPort,
    private val identityApi: IdentityApi,
    private val mailSender: MailSenderService,
) : WorkflowAction {
    override val type: String = "send_email_to_sales_owner"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        if (ctx.event.entityType != "Lead") {
            throw IllegalStateException("send_email_to_sales_owner requires a Lead event")
        }
        val templateId =
            ctx.action["emailTemplateId"]?.toString()
                ?: throw IllegalArgumentException("send_email_to_sales_owner.action.emailTemplateId is required")
        val template =
            settingsApi.findEmailTemplateById(UUID.fromString(templateId))
                ?: throw NoSuchElementException("Email template not found: $templateId")
        val ownerId =
            targetPort.findLeadOwnerId(ctx.event.entityId)
                ?: throw IllegalStateException("Lead ${ctx.event.entityId} has no assigned user")
        val owner =
            identityApi.findById(ownerId)
                ?: throw NoSuchElementException("User not found: $ownerId")
        mailSender.sendHtmlEmail(owner.email, template.subject, template.content)
        return mapOf("leadId" to ctx.event.entityId, "ownerId" to ownerId, "to" to owner.email)
    }
}
