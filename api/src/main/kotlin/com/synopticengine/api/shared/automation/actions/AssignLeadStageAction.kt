package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AssignLeadStageAction(
    private val targetPort: WorkflowTargetPort,
) : WorkflowAction {
    override val type: String = "assign_stage"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        if (ctx.event.entityType != "Lead") {
            throw IllegalStateException("assign_stage requires a Lead event")
        }
        val stageId = UUID.fromString(ctx.action["stageId"]?.toString() ?: throw IllegalArgumentException("stageId is required"))
        val leadId = targetPort.assignLeadStage(ctx.event.entityId, stageId)
            ?: throw NoSuchElementException("Lead/stage not found for ${ctx.event.entityId}")
        return mapOf("leadId" to leadId, "stageId" to stageId)
    }
}
