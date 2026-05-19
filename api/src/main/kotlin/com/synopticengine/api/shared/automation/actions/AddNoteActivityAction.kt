package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Component

/**
 * `add_note_as_activity` — create a NOTE activity attached to the triggering
 * record. Notes are auto-marked done at creation time.
 */
@Component
class AddNoteActivityAction(
    private val targetPort: WorkflowTargetPort,
) : WorkflowAction {
    override val type: String = "add_note_as_activity"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        val title = ctx.action["title"]?.toString() ?: "Workflow note"
        val comment = ctx.action["comment"]?.toString()
        val activityId = targetPort.createNoteActivity(ctx.event.entityType, ctx.event.entityId, title, comment)
        return mapOf("activityId" to activityId, "entityId" to ctx.event.entityId)
    }
}
