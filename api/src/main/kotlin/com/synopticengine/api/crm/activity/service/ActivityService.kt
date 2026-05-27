package com.synopticengine.api.crm.activity.service

import com.synopticengine.api.crm.activity.domain.Activity
import com.synopticengine.api.crm.activity.domain.ActivityFile
import com.synopticengine.api.crm.activity.domain.ActivityParticipant
import com.synopticengine.api.crm.activity.domain.ActivityType
import com.synopticengine.api.crm.activity.repo.ActivityFileRepository
import com.synopticengine.api.crm.activity.repo.ActivityParticipantRepository
import com.synopticengine.api.crm.activity.repo.ActivityRepository
import com.synopticengine.api.crm.activity.web.ActivityFileResponse
import com.synopticengine.api.crm.activity.web.ActivityParticipantResponse
import com.synopticengine.api.crm.activity.web.ActivityResponse
import com.synopticengine.api.crm.scoping.ScopeResolver
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.security.requireOwnership
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
    private val activityParticipantRepository: ActivityParticipantRepository,
    private val storageService: StorageService,
    private val scopeResolver: ScopeResolver,
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
    ): PageResponse<ActivityResponse> {
        val scopeIds = scopeResolver.userIdsForCurrentUser()
        val page =
            if (scopeIds == null) {
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
                )
            } else {
                activityRepository.filterScoped(
                    leadId,
                    personId,
                    organizationId,
                    userId,
                    type,
                    isDone,
                    productId,
                    warehouseId,
                    scopeIds,
                    pageable,
                )
            }
        return PageResponse.of(page) { it.toResponseWithParticipants() }
    }

    fun findById(id: UUID): ActivityResponse =
        (
            activityRepository.findByIdAndDeletedAtIsNull(
                id,
            ) ?: throw NoSuchElementException("Activity not found: $id")
        ).toResponseWithParticipants()

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
        assertNoMeetingOverlap(type, scheduleFrom, scheduleTo, userId, personId, null)
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
            ).toResponseWithParticipants()
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
        assertNoMeetingOverlap(type, scheduleFrom, scheduleTo, userId, personId, id)
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
        return activityRepository.save(activity).toResponseWithParticipants()
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

    private fun assertNoMeetingOverlap(
        type: ActivityType,
        from: Instant?,
        to: Instant?,
        userId: UUID?,
        personId: UUID?,
        excludeActivityId: UUID?,
    ) {
        if (type != ActivityType.MEETING || from == null || to == null) return
        val overlaps =
            checkOverlap(
                start = from,
                end = to,
                userIds = userId?.let(::listOf) ?: emptyList(),
                personIds = personId?.let(::listOf) ?: emptyList(),
                excludeActivityId = excludeActivityId,
            )
        if (overlaps.isNotEmpty()) {
            throw IllegalStateException("Meeting schedule overlaps with an existing meeting")
        }
    }

    @Transactional
    fun toggleDone(id: UUID): ActivityResponse {
        val activity = requireActivity(id)
        activity.isDone = !activity.isDone
        return activityRepository.save(activity).toResponseWithParticipants()
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
    fun addUserParticipant(
        activityId: UUID,
        userId: UUID,
    ): ActivityResponse {
        val activity = requireActivity(activityId)
        // Idempotent: a (activity, user) pair is unique by intent.
        if (activityParticipantRepository.findByActivityIdAndUserId(activityId, userId) == null) {
            activityParticipantRepository.save(
                ActivityParticipant().apply {
                    this.activityId = activityId
                    this.userId = userId
                },
            )
        }
        return activity.toResponseWithParticipants()
    }

    @Transactional
    fun addPersonParticipant(
        activityId: UUID,
        personId: UUID,
    ): ActivityResponse {
        val activity = requireActivity(activityId)
        if (activityParticipantRepository.findByActivityIdAndPersonId(activityId, personId) == null) {
            activityParticipantRepository.save(
                ActivityParticipant().apply {
                    this.activityId = activityId
                    this.personId = personId
                },
            )
        }
        return activity.toResponseWithParticipants()
    }

    @Transactional
    fun removeUserParticipant(
        activityId: UUID,
        userId: UUID,
    ): ActivityResponse {
        val activity = requireActivity(activityId)
        activityParticipantRepository.deleteByActivityIdAndUserId(activityId, userId)
        return activity.toResponseWithParticipants()
    }

    @Transactional
    fun removePersonParticipant(
        activityId: UUID,
        personId: UUID,
    ): ActivityResponse {
        val activity = requireActivity(activityId)
        activityParticipantRepository.deleteByActivityIdAndPersonId(activityId, personId)
        return activity.toResponseWithParticipants()
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
            activityFileRepository.findByIdAndActivityId(fileId, activityId)
                ?: throw NoSuchElementException("File not found: $fileId")
        val bytes = storageService.load(file.path)
        return Triple(bytes, file.contentType ?: "application/octet-stream", file.name)
    }

    /** P3.6 — calendar view, activities whose [scheduleFrom, scheduleTo) intersects [start, end). */
    fun calendar(
        start: Instant,
        end: Instant,
    ): List<ActivityResponse> {
        if (end.isBefore(start)) {
            throw IllegalArgumentException("end must be on or after start")
        }
        return activityRepository.findCalendarRange(start, end).map { it.toResponseWithParticipants() }
    }

    /**
     * P3.6 — meeting overlap check. Given a proposed window plus participant
     * sets, return every existing MEETING that overlaps. Callers may pass
     * [excludeActivityId] when validating an update so a meeting doesn't
     * overlap with itself.
     */
    fun checkOverlap(
        start: Instant,
        end: Instant,
        userIds: List<UUID>,
        personIds: List<UUID>,
        excludeActivityId: UUID?,
    ): List<ActivityResponse> {
        if (end.isBefore(start)) {
            throw IllegalArgumentException("end must be on or after start")
        }
        if (userIds.isEmpty() && personIds.isEmpty()) return emptyList()
        // Native query — pass tenant explicitly. The `activities` table is not
        // RLS-protected, so the tenant predicate inside the query is the only
        // isolation layer.
        val tenantId = TenantContext.get() ?: error("TenantContext not set; meeting overlap requires authentication")
        return activityRepository
            .findOverlappingMeetings(
                tenantId,
                start,
                end,
                userIds.toTypedArray(),
                personIds.toTypedArray(),
                excludeActivityId,
            ).map { it.toResponseWithParticipants() }
    }

    private fun requireActivity(id: UUID): Activity {
        val a =
            activityRepository.findByIdAndDeletedAtIsNull(id)
                ?: throw NoSuchElementException("Activity not found: $id")
        a.requireOwnership()
        return a
    }

    private fun Activity.toResponseWithParticipants(): ActivityResponse {
        val participants = activityParticipantRepository.findAllByActivityId(id!!)
        return ActivityResponse(
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
            participantIds = participants.mapNotNull { it.userId },
            participantPersonIds = participants.mapNotNull { it.personId },
            participants =
                participants.map {
                    ActivityParticipantResponse(
                        id = it.id!!,
                        userId = it.userId,
                        personId = it.personId,
                    )
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

fun ActivityFile.toFileResponse() =
    ActivityFileResponse(
        id = id!!,
        activityId = activityId,
        name = name,
        size = size,
        contentType = contentType,
        createdAt = createdAt,
    )
