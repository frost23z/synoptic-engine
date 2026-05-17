package com.synopticengine.api.crm.activity.service

import com.synopticengine.api.crm.activity.domain.Activity
import com.synopticengine.api.crm.activity.domain.ActivityFile
import com.synopticengine.api.crm.activity.domain.ActivityType
import com.synopticengine.api.crm.activity.repo.ActivityFileRepository
import com.synopticengine.api.crm.activity.repo.ActivityRepository
import com.synopticengine.api.crm.activity.web.ActivityFileResponse
import com.synopticengine.api.crm.activity.web.ActivityResponse
import com.synopticengine.api.shared.storage.StorageService
import com.synopticengine.api.shared.web.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ActivityService(
    private val activityRepository: ActivityRepository,
    private val activityFileRepository: ActivityFileRepository,
    private val storageService: StorageService,
) {
    fun filter(
        leadId: UUID?,
        personId: UUID?,
        organizationId: UUID? = null,
        userId: UUID?,
        type: ActivityType?,
        isDone: Boolean?,
        productId: UUID?,
        warehouseId: UUID?,
        pageable: Pageable,
    ): PageResponse<ActivityResponse> =
        PageResponse.of(
            activityRepository.filter(
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
        ) { it.toResponse() }

    fun findById(id: UUID): ActivityResponse =
        (
            activityRepository.findByIdAndDeletedAtIsNull(
                id,
            ) ?: throw NoSuchElementException("Activity not found: $id")
        ).toResponse()

    @Transactional
    fun create(
        title: String,
        type: ActivityType,
        scheduleFrom: Instant?,
        scheduleTo: Instant?,
        comment: String?,
        leadId: UUID?,
        userId: UUID?,
        personId: UUID?,
        organizationId: UUID?,
        productId: UUID? = null,
        warehouseId: UUID? = null,
        location: String? = null,
        additional: String? = null,
    ): ActivityResponse {
        validateSchedule(type, scheduleFrom, scheduleTo)
        return activityRepository
            .save(
                Activity().apply {
                    this.title = title
                    this.type = type
                    this.scheduleFrom = scheduleFrom
                    this.scheduleTo = scheduleTo
                    this.location = location
                    this.additional = additional
                    this.comment = comment
                    this.leadId = leadId
                    this.userId = userId
                    this.personId = personId
                    this.organizationId = organizationId
                    this.productId = productId
                    this.warehouseId = warehouseId
                    // Notes are inherently completed the moment they're recorded.
                    this.isDone = (type == ActivityType.NOTE)
                },
            ).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        title: String,
        type: ActivityType,
        scheduleFrom: Instant?,
        scheduleTo: Instant?,
        comment: String?,
        isDone: Boolean,
        leadId: UUID?,
        userId: UUID?,
        personId: UUID?,
        organizationId: UUID?,
        productId: UUID? = null,
        warehouseId: UUID? = null,
        location: String? = null,
        additional: String? = null,
    ): ActivityResponse {
        validateSchedule(type, scheduleFrom, scheduleTo)
        val activity = requireActivity(id)
        activity.title = title
        activity.type = type
        activity.scheduleFrom = scheduleFrom
        activity.scheduleTo = scheduleTo
        activity.location = location
        activity.additional = additional
        activity.comment = comment
        activity.isDone = isDone
        activity.leadId = leadId
        activity.userId = userId
        activity.personId = personId
        activity.organizationId = organizationId
        activity.productId = productId
        activity.warehouseId = warehouseId
        return activityRepository.save(activity).toResponse()
    }

    private fun validateSchedule(
        type: ActivityType,
        from: Instant?,
        to: Instant?,
    ) {
        val scheduleRequired = type != ActivityType.NOTE && type != ActivityType.FILE
        if (scheduleRequired && (from == null || to == null)) {
            throw IllegalArgumentException("scheduleFrom and scheduleTo are required for activities of type $type")
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw IllegalArgumentException("scheduleTo must be on or after scheduleFrom")
        }
    }

    @Transactional
    fun toggleDone(id: UUID): ActivityResponse {
        val activity = requireActivity(id)
        activity.isDone = !activity.isDone
        return activityRepository.save(activity).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val activity = requireActivity(id)
        activity.deletedAt = Instant.now()
        activityRepository.save(activity)
    }

    @Transactional
    fun massUpdate(
        ids: List<UUID>,
        userId: UUID?,
        isDone: Boolean?,
    ) {
        ids.forEach { id ->
            activityRepository.findByIdAndDeletedAtIsNull(id)?.let { activity ->
                if (userId != null) activity.userId = userId
                if (isDone != null) activity.isDone = isDone
                activityRepository.save(activity)
            }
        }
    }

    @Transactional
    fun massDestroy(ids: List<UUID>) {
        ids.forEach { id ->
            activityRepository.findByIdAndDeletedAtIsNull(id)?.let { activity ->
                activity.deletedAt = Instant.now()
                activityRepository.save(activity)
            }
        }
    }

    @Transactional
    fun addParticipant(
        activityId: UUID,
        userId: UUID,
    ): ActivityResponse {
        val activity = requireActivity(activityId)
        activity.participantIds.add(userId)
        return activityRepository.save(activity).toResponse()
    }

    @Transactional
    fun removeParticipant(
        activityId: UUID,
        userId: UUID,
    ): ActivityResponse {
        val activity = requireActivity(activityId)
        activity.participantIds.remove(userId)
        return activityRepository.save(activity).toResponse()
    }

    @Transactional
    fun uploadFile(
        activityId: UUID,
        originalFilename: String,
        bytes: ByteArray,
        contentType: String,
    ): ActivityFileResponse {
        activityRepository.findByIdAndDeletedAtIsNull(activityId)
            ?: throw NoSuchElementException("Activity not found: $activityId")
        val storedPath =
            storageService.store(
                directory = "activities/$activityId",
                filename = "${UUID.randomUUID()}_$originalFilename",
                bytes = bytes,
                contentType = contentType,
            )
        val file =
            activityFileRepository.save(
                ActivityFile().apply {
                    this.activityId = activityId
                    this.name = originalFilename
                    this.path = storedPath
                    this.size = bytes.size.toLong()
                    this.contentType = contentType
                },
            )
        return file.toFileResponse()
    }

    fun downloadFile(
        activityId: UUID,
        fileId: UUID,
    ): Triple<ByteArray, String, String> {
        activityRepository.findByIdAndDeletedAtIsNull(activityId)
            ?: throw NoSuchElementException("Activity not found: $activityId")
        val file =
            activityFileRepository
                .findById(fileId)
                .orElseThrow { NoSuchElementException("File not found: $fileId") }
        val bytes = storageService.load(file.path)
        return Triple(bytes, file.contentType ?: "application/octet-stream", file.name)
    }

    private fun requireActivity(id: UUID): Activity =
        activityRepository.findByIdAndDeletedAtIsNull(id) ?: throw NoSuchElementException("Activity not found: $id")
}

fun Activity.toResponse() =
    ActivityResponse(
        id = id!!,
        title = title,
        type = type.name,
        comment = comment,
        location = location,
        additional = additional,
        isDone = isDone,
        scheduleFrom = scheduleFrom,
        scheduleTo = scheduleTo,
        leadId = leadId,
        userId = userId,
        personId = personId,
        organizationId = organizationId,
        productId = productId,
        warehouseId = warehouseId,
        participantIds = participantIds.toList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ActivityFile.toFileResponse() =
    ActivityFileResponse(
        id = id!!,
        activityId = activityId,
        name = name,
        size = size,
        contentType = contentType,
        createdAt = createdAt,
    )
