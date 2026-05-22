package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AssignLeadUserAction(
    private val targetPort: WorkflowTargetPort,
) : WorkflowAction {
    override val type: String = "assign_user"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        if (ctx.event.entityType != "Lead") {
            throw IllegalStateException("assign_user requires a Lead event")
        }
        val userId = UUID.fromString(ctx.action["userId"]?.toString() ?: throw IllegalArgumentException("userId is required"))
        val leadId = targetPort.assignLeadUser(ctx.event.entityId, userId)
            ?: throw NoSuchElementException("Lead not found: ${ctx.event.entityId}")
        return mapOf("leadId" to leadId, "userId" to userId)
    }
}
