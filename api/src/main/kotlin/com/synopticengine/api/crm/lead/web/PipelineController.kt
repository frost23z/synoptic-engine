package com.synopticengine.api.crm.lead.web

import com.synopticengine.api.crm.lead.service.PipelineService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/pipelines")
class PipelineController(
    private val pipelineService: PipelineService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('leads.view')")
    fun listAll(): ResponseEntity<List<PipelineResponse>> = ResponseEntity.ok(pipelineService.findAll())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<PipelineResponse> = ResponseEntity.ok(pipelineService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('pipelines.create')")
    fun create(
        @Valid @RequestBody request: CreatePipelineRequest,
    ): ResponseEntity<PipelineResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(pipelineService.create(request.name, request.description, request.isActive, request.rottenDays))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdatePipelineRequest,
    ): ResponseEntity<PipelineResponse> =
        ResponseEntity.ok(
            pipelineService.update(
                id,
                request.name,
                request.description,
                request.isActive,
                request.isDefault,
                request.rottenDays,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        pipelineService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/stages")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun addStage(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateStageRequest,
    ): ResponseEntity<StageResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                pipelineService.addStage(
                    id,
                    request.name,
                    request.sortOrder,
                    request.color,
                    request.probability,
                    request.code,
                ),
            )

    @PutMapping("/{id}/stages/{stageId}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun updateStage(
        @PathVariable id: UUID,
        @PathVariable stageId: UUID,
        @Valid @RequestBody request: UpdateStageRequest,
    ): ResponseEntity<StageResponse> =
        ResponseEntity.ok(
            pipelineService.updateStage(
                id,
                stageId,
                request.name,
                request.sortOrder,
                request.color,
                request.probability,
                request.code,
            ),
        )

    @DeleteMapping("/{id}/stages/{stageId}")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun deleteStage(
        @PathVariable id: UUID,
        @PathVariable stageId: UUID,
    ): ResponseEntity<Void> {
        pipelineService.deleteStage(id, stageId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/stages/reorder")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun reorderStagesPut(
        @PathVariable id: UUID,
        @RequestBody request: ReorderStagesRequest,
    ): ResponseEntity<PipelineResponse> = ResponseEntity.ok(pipelineService.reorderStages(id, request.order))

    @PatchMapping("/{id}/stages/reorder")
    @PreAuthorize("hasAuthority('leads.edit')")
    fun reorderStages(
        @PathVariable id: UUID,
        @RequestBody request: ReorderStagesRequest,
    ): ResponseEntity<PipelineResponse> = ResponseEntity.ok(pipelineService.reorderStages(id, request.order))
}
