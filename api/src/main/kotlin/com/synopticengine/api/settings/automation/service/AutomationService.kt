package com.synopticengine.api.settings.automation.service

import com.synopticengine.api.settings.automation.domain.Webhook
import com.synopticengine.api.settings.automation.domain.Workflow
import com.synopticengine.api.settings.automation.domain.WorkflowActionRun
import com.synopticengine.api.settings.automation.repo.WebhookRepository
import com.synopticengine.api.settings.automation.repo.WorkflowActionRunRepository
import com.synopticengine.api.settings.automation.repo.WorkflowRepository
import com.synopticengine.api.settings.automation.web.WebhookResponse
import com.synopticengine.api.settings.automation.web.WorkflowActionRunResponse
import com.synopticengine.api.settings.automation.web.WorkflowResponse
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AutomationService(
    private val workflowRepository: WorkflowRepository,
    private val webhookRepository: WebhookRepository,
    private val actionRunRepository: WorkflowActionRunRepository,
) {
    // ── Workflows ─────────────────────────────────────────────────────────

    fun findAllWorkflows(): List<WorkflowResponse> = workflowRepository.findAll().map { it.toResponse() }

    fun findWorkflowById(id: UUID): WorkflowResponse =
        workflowRepository.findById(id).orElseThrow { NoSuchElementException("Workflow not found: $id") }.toResponse()

    @Transactional
    fun createWorkflow(
        name: String,
        description: String?,
        eventName: String,
        conditions: List<Map<String, Any?>>,
        actions: List<Map<String, Any?>>,
        isActive: Boolean,
        conditionType: String = "and",
    ): WorkflowResponse =
        workflowRepository
            .save(
                Workflow().apply {
                    this.name = name
                    this.description = description
                    this.eventName = eventName
                    this.conditions = conditions
                    this.actions = actions
                    this.conditionType = conditionType.lowercase()
                    this.isActive = isActive
                },
            ).toResponse()

    @Transactional
    fun updateWorkflow(
        id: UUID,
        name: String,
        description: String?,
        eventName: String,
        conditions: List<Map<String, Any?>>,
        actions: List<Map<String, Any?>>,
        isActive: Boolean,
        conditionType: String = "and",
    ): WorkflowResponse {
        val workflow = workflowRepository.findById(id).orElseThrow { NoSuchElementException("Workflow not found: $id") }
        workflow.name = name
        workflow.description = description
        workflow.eventName = eventName
        workflow.conditions = conditions
        workflow.actions = actions
        workflow.conditionType = conditionType.lowercase()
        workflow.isActive = isActive
        return workflowRepository.save(workflow).toResponse()
    }

    @Transactional
    fun deleteWorkflow(id: UUID) {
        if (!workflowRepository.existsById(id)) throw NoSuchElementException("Workflow not found: $id")
        workflowRepository.deleteById(id)
    }

    fun listRuns(
        workflowId: UUID,
        pageable: Pageable,
    ): PageResponse<WorkflowActionRunResponse> {
        if (!workflowRepository.existsById(workflowId)) throw NoSuchElementException("Workflow not found: $workflowId")
        return PageResponse.of(
            actionRunRepository.findAllByWorkflowIdOrderByCreatedAtDesc(workflowId, pageable),
        ) { it.toResponse() }
    }

    // ── Webhooks ──────────────────────────────────────────────────────────

    fun findAllWebhooks(): List<WebhookResponse> = webhookRepository.findAll().map { it.toResponse() }

    fun findWebhookById(id: UUID): WebhookResponse =
        webhookRepository.findById(id).orElseThrow { NoSuchElementException("Webhook not found: $id") }.toResponse()

    @Transactional
    fun createWebhook(
        name: String,
        payloadUrl: String,
        events: List<String>,
        isActive: Boolean,
    ): WebhookResponse =
        webhookRepository
            .save(
                Webhook().apply {
                    this.name = name
                    this.payloadUrl = payloadUrl
                    this.events = events
                    this.isActive = isActive
                },
            ).toResponse()

    @Transactional
    fun updateWebhook(
        id: UUID,
        name: String,
        payloadUrl: String,
        events: List<String>,
        isActive: Boolean,
    ): WebhookResponse {
        val webhook = webhookRepository.findById(id).orElseThrow { NoSuchElementException("Webhook not found: $id") }
        webhook.name = name
        webhook.payloadUrl = payloadUrl
        webhook.events = events
        webhook.isActive = isActive
        return webhookRepository.save(webhook).toResponse()
    }

    @Transactional
    fun deleteWebhook(id: UUID) {
        if (!webhookRepository.existsById(id)) throw NoSuchElementException("Webhook not found: $id")
        webhookRepository.deleteById(id)
    }
}

fun Workflow.toResponse() =
    WorkflowResponse(
        id = id!!,
        name = name,
        description = description,
        eventName = eventName,
        conditions = conditions,
        actions = actions,
        conditionType = conditionType,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Webhook.toResponse() =
    WebhookResponse(
        id = id!!,
        name = name,
        payloadUrl = payloadUrl,
        events = events,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WorkflowActionRun.toResponse() =
    WorkflowActionRunResponse(
        id = id!!,
        workflowId = workflowId,
        eventName = eventName,
        entityType = entityType,
        entityId = entityId,
        actionType = actionType,
        status = status.name,
        errorMessage = errorMessage,
        payload = payload,
        createdAt = createdAt,
    )
