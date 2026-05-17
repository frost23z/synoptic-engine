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
    @PreAuthorize("hasAuthority('automations.edit')")
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
    @PreAuthorize("hasAuthority('automations.edit')")
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
}
