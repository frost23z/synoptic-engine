package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Component

/**
 * `update_lead` — set a field on the triggering lead.
 *
 * Action payload: `{ "type": "update_lead", "field": "<column>", "value": "<value>" }`.
 * Supported fields are enumerated in [WorkflowTargetPort.updateLeadField].
 */
@Component
class UpdateLeadAction(
    private val targetPort: WorkflowTargetPort,
) : WorkflowAction {
    override val type: String = "update_lead"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        if (ctx.event.entityType != "Lead") {
            throw IllegalStateException("update_lead requires a Lead-typed event; got ${ctx.event.entityType}")
        }
        val field =
            ctx.action["field"]?.toString()
                ?: throw IllegalArgumentException("update_lead.action.field is required")
        val value = ctx.action["value"]?.toString()
        val leadId =
            targetPort.updateLeadField(ctx.event.entityId, field, value)
                ?: throw NoSuchElementException("Lead not found: ${ctx.event.entityId}")
        return mapOf("leadId" to leadId, "field" to field, "value" to value)
    }
}
