package com.synopticengine.api.crm.activity.web

import com.synopticengine.api.crm.activity.domain.ActivityType
import com.synopticengine.api.crm.activity.service.ActivityService
import com.synopticengine.api.shared.web.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/activities")
class ActivityController(
    private val activityService: ActivityService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('activities.view')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam leadId: UUID?,
        @RequestParam personId: UUID?,
        @RequestParam organizationId: UUID?,
        @RequestParam userId: UUID?,
        @RequestParam type: ActivityType?,
        @RequestParam isDone: Boolean?,
        @RequestParam productId: UUID?,
        @RequestParam warehouseId: UUID?,
    ): ResponseEntity<PageResponse<ActivityResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("scheduleFrom").descending())
        return ResponseEntity.ok(
            activityService.filter(
                leadId,
                personId,
                organizationId,
                userId,
                type,
                isDone,
                productId,
                warehouseId,
                pageable,
            ),
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('activities.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('activities.edit')")
    fun create(
        @Valid @RequestBody request: CreateActivityRequest,
    ): ResponseEntity<ActivityResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                activityService.create(
                    title = request.title,
                    type = request.type,
                    scheduleFrom = request.scheduleFrom,
                    scheduleTo = request.scheduleTo,
                    comment = request.comment,
                    leadId = request.leadId,
                    userId = request.userId,
                    personId = request.personId,
                    organizationId = request.organizationId,
                    productId = request.productId,
                    warehouseId = request.warehouseId,
                    location = request.location,
                    additional = request.additional,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateActivityRequest,
    ): ResponseEntity<ActivityResponse> =
        ResponseEntity.ok(
            activityService.update(
                id = id,
                title = request.title,
                type = request.type,
                scheduleFrom = request.scheduleFrom,
                scheduleTo = request.scheduleTo,
                comment = request.comment,
                isDone = request.isDone,
                leadId = request.leadId,
                userId = request.userId,
                personId = request.personId,
                organizationId = request.organizationId,
                productId = request.productId,
                warehouseId = request.warehouseId,
                location = request.location,
                additional = request.additional,
            ),
        )

    @PatchMapping("/{id}/done")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun toggleDone(
        @PathVariable id: UUID,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.toggleDone(id))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('activities.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        activityService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-update")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun massUpdate(
        @RequestBody request: MassUpdateActivityRequest,
    ): ResponseEntity<Void> {
        activityService.massUpdate(request.ids, request.userId, request.isDone)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('activities.delete')")
    fun massDestroy(
        @RequestBody request: MassDestroyActivityRequest,
    ): ResponseEntity<Void> {
        activityService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/participants")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun addParticipant(
        @PathVariable id: UUID,
        @RequestBody request: AddParticipantRequest,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.addParticipant(id, request.userId))

    @DeleteMapping("/{id}/participants/{userId}")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun removeParticipant(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.removeParticipant(id, userId))

    @PostMapping("/{id}/file", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAuthority('activities.edit')")
    fun uploadFile(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ActivityFileResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            activityService.uploadFile(
                activityId = id,
                originalFilename = file.originalFilename ?: file.name,
                bytes = file.bytes,
                contentType = file.contentType ?: "application/octet-stream",
            ),
        )

    @GetMapping("/{id}/file/{fileId}/download")
    @PreAuthorize("hasAuthority('activities.view')")
    fun downloadFile(
        @PathVariable id: UUID,
        @PathVariable fileId: UUID,
    ): ResponseEntity<ByteArray> {
        val (bytes, contentType, filename) = activityService.downloadFile(id, fileId)
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(contentType)
        headers.contentDisposition = ContentDisposition.attachment().filename(filename).build()
        return ResponseEntity.ok().headers(headers).body(bytes)
    }
}
