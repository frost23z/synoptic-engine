package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AssignLeadGroupAction(
    private val targetPort: WorkflowTargetPort,
) : WorkflowAction {
    override val type: String = "assign_group"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        if (ctx.event.entityType != "Lead") {
            throw IllegalStateException("assign_group requires a Lead event")
        }
        val groupId =
            UUID.fromString(ctx.action["groupId"]?.toString() ?: throw IllegalArgumentException("groupId is required"))
        val leadId =
            targetPort.assignLeadGroup(ctx.event.entityId, groupId)
                ?: throw NoSuchElementException("Lead/group not found for ${ctx.event.entityId}")
        return mapOf("leadId" to leadId, "groupId" to groupId)
    }
}
