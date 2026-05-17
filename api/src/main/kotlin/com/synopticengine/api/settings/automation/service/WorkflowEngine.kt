package com.synopticengine.api.settings.automation.service

import com.synopticengine.api.shared.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class WorkflowEngine(
    private val workflowRepository: com.synopticengine.api.settings.automation.repo.WorkflowRepository,
) {
    private val log = LoggerFactory.getLogger(WorkflowEngine::class.java)

    @EventListener
    @Async
    fun onDomainEvent(event: DomainEvent) {
        val workflows = workflowRepository.findByEventNameAndIsActiveTrue(event.eventName)
        if (workflows.isEmpty()) return

        for (workflow in workflows) {
            try {
                if (evaluateConditions(workflow.conditions, event.payload)) {
                    executeActions(workflow.actions, event)
                }
            } catch (e: Exception) {
                log.warn("Workflow ${workflow.id} failed for event ${event.eventName}: ${e.message}")
            }
        }
    }

    private fun evaluateConditions(
        conditions: List<Map<String, String>>,
        payload: Map<String, Any?>,
    ): Boolean =
        conditions.all { condition ->
            val field = condition["field"] ?: return@all true
            val operator = condition["operator"] ?: "equals"
            val expected = condition["value"] ?: ""
            val actual = payload[field]?.toString() ?: ""
            when (operator) {
                "equals" -> actual == expected
                "not_equals" -> actual != expected
                "contains" -> actual.contains(expected, ignoreCase = true)
                "starts_with" -> actual.startsWith(expected, ignoreCase = true)
                else -> true
            }
        }

    private fun executeActions(
        actions: List<Map<String, String>>,
        event: DomainEvent,
    ) {
        for (action in actions) {
            val type = action["type"] ?: continue
            when (type) {
                "LOG" -> log.info("Workflow action LOG: entity=${event.entityType} id=${event.entityId}")

                // SEND_EMAIL, CREATE_ACTIVITY, UPDATE_FIELD handled by future phases
                else -> log.debug("Workflow action type '$type' not yet implemented")
            }
        }
    }
}
