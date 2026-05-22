package com.synopticengine.api.settings.automation.service

import com.synopticengine.api.settings.automation.domain.WorkflowActionRun
import com.synopticengine.api.settings.automation.domain.WorkflowActionRunStatus
import com.synopticengine.api.settings.automation.repo.WorkflowActionRunRepository
import com.synopticengine.api.settings.automation.repo.WorkflowRepository
import com.synopticengine.api.shared.DomainEvent
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.automation.WorkflowAction
import com.synopticengine.api.shared.automation.WorkflowActionContext
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Phase 3 / P3.2 — workflow execution engine.
 *
 * - Listens for any [DomainEvent].
 * - Loads the workflows registered for `event.eventName` (and the tenant the
 *   event was raised in).
 * - For each workflow, evaluates conditions, dispatches to the matching
 *   [WorkflowAction] strategy, and persists a row in `workflow_action_runs`
 *   describing the outcome — SUCCESS, FAILED, or SKIPPED (filtered out by
 *   conditions or no matching action registered).
 *
 * The engine runs `@Async` so domain-event publication stays non-blocking.
 * Each workflow's actions execute in their own `REQUIRES_NEW` transaction so
 * one bad action can't poison the others.
 */
@Component
class WorkflowEngine(
    private val workflowRepository: WorkflowRepository,
    private val actionRunRepository: WorkflowActionRunRepository,
    private val conditionEvaluator: WorkflowConditionEvaluator,
    actions: List<WorkflowAction>,
) {
    private val log = LoggerFactory.getLogger(WorkflowEngine::class.java)
    private val actionsByType: Map<String, WorkflowAction> =
        actions
            .flatMap { action -> listOf(action.type to action, action.type.replace("_", "-") to action) }
            .toMap()

    @EventListener
    @Async
    fun onDomainEvent(event: DomainEvent) {
        val tenantId = TenantContext.get()
        if (tenantId == null) {
            log.debug("Skipping workflow engine: no tenant context for event ${event.eventName}")
            return
        }
        // Workflows live per tenant; the tenant filter on the repo guards that.
        val workflows = workflowRepository.findByEventNameAndIsActiveTrue(event.eventName)
        if (workflows.isEmpty()) return

        for (workflow in workflows) {
            try {
                val passed = conditionEvaluator.evaluate(workflow.conditions, workflow.conditionType, event.payload)
                if (!passed) {
                    persistRun(workflow.id!!, event, "<conditions>", WorkflowActionRunStatus.SKIPPED, null, null)
                    continue
                }
                for (action in workflow.actions) {
                    runOne(workflow.id!!, event, action)
                }
            } catch (e: Exception) {
                // Top-level catch: per-workflow failures shouldn't cascade.
                log.warn("Workflow ${workflow.id} failed for event ${event.eventName}: ${e.message}")
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun runOne(
        workflowId: UUID,
        event: DomainEvent,
        action: Map<String, Any?>,
    ) {
        val type = action["type"]?.toString()
        if (type.isNullOrBlank()) {
            persistRun(workflowId, event, "<missing-type>", WorkflowActionRunStatus.SKIPPED, "Action has no type", null)
            return
        }
        val strategy = actionsByType[type]
        if (strategy == null) {
            persistRun(workflowId, event, type, WorkflowActionRunStatus.SKIPPED, "No strategy for type '$type'", null)
            return
        }
        try {
            val result = strategy.execute(WorkflowActionContext(event = event, action = action))
            persistRun(workflowId, event, type, WorkflowActionRunStatus.SUCCESS, null, result)
        } catch (e: Exception) {
            log.warn(
                "Workflow $workflowId action '$type' failed for ${event.entityType}:${event.entityId}: ${e.message}",
            )
            persistRun(workflowId, event, type, WorkflowActionRunStatus.FAILED, e.message, null)
        }
    }

    private fun persistRun(
        workflowId: UUID,
        event: DomainEvent,
        actionType: String,
        status: WorkflowActionRunStatus,
        errorMessage: String?,
        payload: Map<String, Any?>?,
    ) {
        actionRunRepository.save(
            WorkflowActionRun().apply {
                this.workflowId = workflowId
                this.eventName = event.eventName
                this.entityType = event.entityType
                this.entityId = event.entityId
                this.actionType = actionType
                this.status = status
                this.errorMessage = errorMessage
                this.payload = payload
            },
        )
    }
}
