package com.synopticengine.api.settings.automation.service

import com.synopticengine.api.settings.automation.domain.Webhook
import com.synopticengine.api.settings.automation.domain.WebhookDeliveryRun
import com.synopticengine.api.settings.automation.domain.Workflow
import com.synopticengine.api.settings.automation.domain.WorkflowActionRun
import com.synopticengine.api.settings.automation.repo.WebhookDeliveryRunRepository
import com.synopticengine.api.settings.automation.repo.WebhookRepository
import com.synopticengine.api.settings.automation.repo.WorkflowActionRunRepository
import com.synopticengine.api.settings.automation.repo.WorkflowRepository
import com.synopticengine.api.settings.automation.web.WebhookDeliveryRunResponse
import com.synopticengine.api.settings.automation.web.WebhookResponse
import com.synopticengine.api.settings.automation.web.WorkflowActionRunResponse
import com.synopticengine.api.settings.automation.web.WorkflowResponse
import com.synopticengine.api.shared.audit.AuditAction
import com.synopticengine.api.shared.audit.AuditLogService
import com.synopticengine.api.shared.security.OutboundUrlValidator
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
    private val webhookDeliveryRunRepository: WebhookDeliveryRunRepository,
    private val outboundUrlValidator: OutboundUrlValidator,
    private val auditLogService: AuditLogService,
    private val webhookDispatcher: WebhookDispatcher,
) {
    // ── Workflows ─────────────────────────────────────────────────────────

    fun findAllWorkflows(): List<WorkflowResponse> = workflowRepository.findAll().map { it.toResponse() }

    fun findWorkflowById(id: UUID): WorkflowResponse = requireWorkflow(id).toResponse()

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
        val workflow = requireWorkflow(id)
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
        // Load through the tenant-aware finder then delete the entity so the
        // filter actually runs before the soft-delete trigger fires.
        workflowRepository.delete(requireWorkflow(id))
    }

    fun listRuns(
        workflowId: UUID,
        pageable: Pageable,
    ): PageResponse<WorkflowActionRunResponse> {
        // requireWorkflow throws 404 (not found) when the caller doesn't own the
        // workflow, which is the right behaviour for cross-tenant probes.
        requireWorkflow(workflowId)
        return PageResponse.of(
            actionRunRepository.findAllByWorkflowIdOrderByCreatedAtDesc(workflowId, pageable),
        ) { it.toResponse() }
    }

    // ── Webhooks ──────────────────────────────────────────────────────────

    fun findAllWebhooks(): List<WebhookResponse> = webhookRepository.findAll().map { it.toResponse() }

    fun findWebhookById(id: UUID): WebhookResponse = requireWebhook(id).toResponse()

    @Transactional
    fun createWebhook(
        name: String,
        payloadUrl: String,
        secret: String?,
        events: List<String>,
        isActive: Boolean,
    ): WebhookResponse {
        // T1.2 — validate at save-time so invalid URLs are rejected before they
        // can ever be dispatched (defense-in-depth; re-validated at send time).
        outboundUrlValidator.validate(payloadUrl)
        val saved =
            webhookRepository
                .save(
                    Webhook().apply {
                        this.name = name
                        this.payloadUrl = payloadUrl
                        this.secret = secret?.takeIf { it.isNotBlank() }
                        this.events = events
                        this.isActive = isActive
                    },
                )
        // T3.4 — audit webhook creation. Secret presence is noted but value is never logged.
        auditLogService.record(
            entityType = "webhook",
            entityId = saved.id?.toString(),
            action = AuditAction.CREATE,
            payload =
                mapOf(
                    "name" to name,
                    "payloadUrl" to payloadUrl,
                    "events" to events,
                    "hasSecret" to !secret.isNullOrBlank(),
                    "isActive" to isActive,
                ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun updateWebhook(
        id: UUID,
        name: String,
        payloadUrl: String,
        secret: String?,
        events: List<String>,
        isActive: Boolean,
    ): WebhookResponse {
        // T1.2 — re-validate on update (URL may change).
        outboundUrlValidator.validate(payloadUrl)
        val webhook = requireWebhook(id)
        webhook.name = name
        webhook.payloadUrl = payloadUrl
        webhook.secret = secret?.takeIf { it.isNotBlank() }
        webhook.events = events
        webhook.isActive = isActive
        val saved = webhookRepository.save(webhook)
        // T3.4 — audit webhook update.
        auditLogService.record(
            entityType = "webhook",
            entityId = id.toString(),
            action = AuditAction.UPDATE,
            payload =
                mapOf(
                    "name" to name,
                    "payloadUrl" to payloadUrl,
                    "events" to events,
                    "hasSecret" to !secret.isNullOrBlank(),
                    "isActive" to isActive,
                ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun deleteWebhook(id: UUID) {
        webhookRepository.delete(requireWebhook(id))
        // T3.4 — audit webhook deletion.
        auditLogService.record(
            entityType = "webhook",
            entityId = id.toString(),
            action = AuditAction.DELETE,
        )
    }

    fun findDeliveriesFor(
        webhookId: UUID,
        pageable: Pageable,
    ): PageResponse<WebhookDeliveryRunResponse> {
        requireWebhook(webhookId)
        return PageResponse.of(
            webhookDeliveryRunRepository.findAllByWebhookIdOrderByCreatedAtDesc(webhookId, pageable),
        ) { it.toResponse() }
    }

    @Transactional
    fun testWebhook(id: UUID): WebhookDeliveryRunResponse = webhookDispatcher.dispatchTest(requireWebhook(id)).toResponse()

    // Tenant-aware loads. JpaRepository.findById bypasses Hibernate's tenant
    // filter (it hits EntityManager.find()); JPQL findActiveById does not.
    private fun requireWorkflow(id: UUID): Workflow =
        workflowRepository.findActiveById(id) ?: throw NoSuchElementException("Workflow not found: $id")

    private fun requireWebhook(id: UUID): Webhook =
        webhookRepository.findActiveById(id) ?: throw NoSuchElementException("Webhook not found: $id")
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
        hasSecret = !secret.isNullOrBlank(),
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

fun WebhookDeliveryRun.toResponse() =
    WebhookDeliveryRunResponse(
        id = id!!,
        webhookId = webhookId,
        eventName = eventName,
        entityType = entityType,
        entityId = entityId,
        status = status.name,
        responseCode = responseCode,
        responseBody = responseBody,
        errorMessage = errorMessage,
        createdAt = createdAt,
    )
