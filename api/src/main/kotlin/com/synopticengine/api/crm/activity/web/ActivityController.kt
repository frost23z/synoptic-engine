package com.synopticengine.api.crm.activity.web

import com.synopticengine.api.crm.activity.domain.ActivityType
import com.synopticengine.api.crm.activity.service.ActivityService
import com.synopticengine.api.shared.upload.FileUploadGuard
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
@RequestMapping("/activities")
class ActivityController(
    private val activityService: ActivityService,
    private val fileUploadGuard: FileUploadGuard,
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
    @PreAuthorize("hasAuthority('activities.create')")
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
        @Valid @RequestBody request: MassUpdateActivityRequest,
    ): ResponseEntity<Void> {
        activityService.massUpdate(request.ids, request.userId, request.isDone)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('activities.delete')")
    fun massDestroy(
        @Valid @RequestBody request: MassDestroyActivityRequest,
    ): ResponseEntity<Void> {
        activityService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    /**
     * Back-compat shim: defaults to a user participant. Callers should migrate to
     * the explicit `/participants/users` and `/participants/persons` endpoints below.
     */
    @PostMapping("/{id}/participants")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun addParticipant(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddParticipantRequest,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.addUserParticipant(id, request.userId))

    @PostMapping("/{id}/participants/users")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun addUserParticipant(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddUserParticipantRequest,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.addUserParticipant(id, request.userId))

    @PostMapping("/{id}/participants/persons")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun addPersonParticipant(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddPersonParticipantRequest,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.addPersonParticipant(id, request.personId))

    @DeleteMapping("/{id}/participants/users/{userId}")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun removeUserParticipant(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.removeUserParticipant(id, userId))

    @DeleteMapping("/{id}/participants/persons/{personId}")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun removePersonParticipant(
        @PathVariable id: UUID,
        @PathVariable personId: UUID,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.removePersonParticipant(id, personId))

    /** Back-compat: original delete-by-userId path stays so existing clients keep working. */
    @DeleteMapping("/{id}/participants/{userId}")
    @PreAuthorize("hasAuthority('activities.edit')")
    fun removeParticipant(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
    ): ResponseEntity<ActivityResponse> = ResponseEntity.ok(activityService.removeUserParticipant(id, userId))

    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('activities.view')")
    fun calendar(
        @RequestParam start: java.time.Instant,
        @RequestParam end: java.time.Instant,
    ): ResponseEntity<List<ActivityResponse>> = ResponseEntity.ok(activityService.calendar(start, end))

    @PostMapping("/check-overlap")
    @PreAuthorize("hasAuthority('activities.view')")
    fun checkOverlap(
        @Valid @RequestBody request: CheckOverlapRequest,
    ): ResponseEntity<CheckOverlapResponse> {
        val overlaps =
            activityService.checkOverlap(
                start = request.scheduleFrom,
                end = request.scheduleTo,
                userIds = request.userIds,
                personIds = request.personIds,
                excludeActivityId = request.excludeActivityId,
            )
        return ResponseEntity.ok(CheckOverlapResponse(hasOverlap = overlaps.isNotEmpty(), overlaps = overlaps))
    }

    @PostMapping("/{id}/file", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAuthority('activities.edit')")
    fun uploadFile(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ActivityFileResponse> {
        // T4.3 — validate MIME type and enforce max size before touching the service layer.
        fileUploadGuard.validateActivityFile(file)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            activityService.uploadFile(
                activityId = id,
                originalFilename = file.originalFilename ?: file.name,
                bytes = file.bytes,
                contentType = file.contentType ?: "application/octet-stream",
            ),
        )
    }

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
