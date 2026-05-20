package com.synopticengine.api.settings.automation.web

import com.synopticengine.api.settings.automation.service.AutomationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/settings/workflows")
class WorkflowController(
    private val automationService: AutomationService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('automations.view')")
    fun listAll(): ResponseEntity<List<WorkflowResponse>> = ResponseEntity.ok(automationService.findAllWorkflows())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('automations.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<WorkflowResponse> = ResponseEntity.ok(automationService.findWorkflowById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('automations.create')")
    fun create(
        @Valid @RequestBody request: CreateWorkflowRequest,
    ): ResponseEntity<WorkflowResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                automationService.createWorkflow(
                    request.name,
                    request.description,
                    request.eventName,
                    request.conditions,
                    request.actions,
                    request.isActive,
                    request.conditionType,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('automations.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWorkflowRequest,
    ): ResponseEntity<WorkflowResponse> =
        ResponseEntity.ok(
            automationService.updateWorkflow(
                id,
                request.name,
                request.description,
                request.eventName,
                request.conditions,
                request.actions,
                request.isActive,
                request.conditionType,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('automations.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        automationService.deleteWorkflow(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/runs")
    @PreAuthorize("hasAuthority('automations.view')")
    fun listRuns(
        @PathVariable id: UUID,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") page: Int,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<com.synopticengine.api.shared.web.PageResponse<WorkflowActionRunResponse>> =
        ResponseEntity.ok(
            automationService.listRuns(
                id,
                org.springframework.data.domain.PageRequest
                    .of(page, size),
            ),
        )
}

@RestController
@RequestMapping($$"${api.base-path}/settings/webhooks")
class WebhookController(
    private val automationService: AutomationService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('automations.view')")
    fun listAll(): ResponseEntity<List<WebhookResponse>> = ResponseEntity.ok(automationService.findAllWebhooks())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('automations.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<WebhookResponse> = ResponseEntity.ok(automationService.findWebhookById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('automations.create')")
    fun create(
        @Valid @RequestBody request: CreateWebhookRequest,
    ): ResponseEntity<WebhookResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(automationService.createWebhook(request.name, request.payloadUrl, request.events, request.isActive))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('automations.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWebhookRequest,
    ): ResponseEntity<WebhookResponse> =
        ResponseEntity.ok(
            automationService.updateWebhook(id, request.name, request.payloadUrl, request.events, request.isActive),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('automations.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        automationService.deleteWebhook(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/deliveries")
    @PreAuthorize("hasAuthority('automations.view')")
    fun listDeliveries(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<com.synopticengine.api.shared.web.PageResponse<WebhookDeliveryRunResponse>> =
        ResponseEntity.ok(
            automationService.findDeliveriesFor(
                id,
                org.springframework.data.domain.PageRequest
                    .of(page, size),
            ),
        )
}
