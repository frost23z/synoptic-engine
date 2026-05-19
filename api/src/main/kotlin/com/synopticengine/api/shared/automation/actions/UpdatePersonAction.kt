package com.synopticengine.api.shared.automation.actions

import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import com.synopticengine.api.shared.automation.WorkflowTargetPort
import org.springframework.stereotype.Component

/**
 * `update_person` — set a field on the linked person. For a Lead-typed event
 * we resolve via the lead's `personId`; for a Person-typed event we use it
 * directly. Supported fields are enumerated in [WorkflowTargetPort.updatePersonField].
 */
@Component
class UpdatePersonAction(
    private val targetPort: WorkflowTargetPort,
) : WorkflowAction {
    override val type: String = "update_person"

    override fun execute(ctx: WorkflowActionContext): Map<String, Any?> {
        val personId =
            when (ctx.event.entityType) {
                "Person" -> {
                    ctx.event.entityId
                }

                "Lead" -> {
                    val pair =
                        targetPort.findLeadPersonAndEmail(ctx.event.entityId)
                            ?: throw NoSuchElementException("Lead not found: ${ctx.event.entityId}")
                    pair.first
                }

                else -> {
                    throw IllegalStateException("update_person requires a Lead or Person event")
                }
            }
        val field =
            ctx.action["field"]?.toString()
                ?: throw IllegalArgumentException("update_person.action.field is required")
        val value = ctx.action["value"]?.toString()
        val updated =
            targetPort.updatePersonField(personId, field, value)
                ?: throw NoSuchElementException("Person not found: $personId")
        return mapOf("personId" to updated, "field" to field, "value" to value)
    }
}
