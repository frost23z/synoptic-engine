package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * `add_tag` — attach a tag to the triggering record. Accepts either:
 *  - `tagId: <uuid>` to attach an existing tag
 *  - `tagName: "<string>"` to find-or-create by name
 *
 * Works with Lead and Person entities.
 */
@Component
class AddTagAction(
    private val targetPort: WorkflowTargetPort,
) : WorkflowAction {
    override val type: String = "add_tag"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        val tagId = ctx.action["tagId"]?.toString()?.let(UUID::fromString)
        val tagName = ctx.action["tagName"]?.toString()
        if (tagId == null && tagName.isNullOrBlank()) {
            throw IllegalArgumentException("add_tag requires tagId or tagName")
        }
        val attached =
            when (ctx.event.entityType) {
                "Lead" -> targetPort.ensureLeadTag(ctx.event.entityId, tagId, tagName)
                "Person" -> targetPort.ensurePersonTag(ctx.event.entityId, tagId, tagName)
                else -> throw IllegalStateException("add_tag does not support entity type ${ctx.event.entityType}")
            } ?: throw NoSuchElementException("Target not found for add_tag: ${ctx.event.entityId}")
        return mapOf("tagId" to attached, "entityId" to ctx.event.entityId)
    }
}
