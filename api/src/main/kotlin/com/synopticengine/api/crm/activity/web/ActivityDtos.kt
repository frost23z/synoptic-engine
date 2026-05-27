package com.synopticengine.api.crm.activity.web

import com.synopticengine.api.crm.activity.domain.ActivityType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateActivityRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    @field:NotNull(message = "Type is required")
    val type: ActivityType,
    /** Required unless type is NOTE or FILE. */
    val scheduleFrom: Instant? = null,
    /** Required unless type is NOTE or FILE. */
    val scheduleTo: Instant? = null,
    val location: String? = null,
    val additional: String? = null,
    val comment: String? = null,
    val leadId: UUID? = null,
    val userId: UUID? = null,
    val personId: UUID? = null,
    val organizationId: UUID? = null,
    val productId: UUID? = null,
    val warehouseId: UUID? = null,
)

data class UpdateActivityRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    @field:NotNull(message = "Type is required")
    val type: ActivityType,
    val scheduleFrom: Instant? = null,
    val scheduleTo: Instant? = null,
    val location: String? = null,
    val additional: String? = null,
    val comment: String? = null,
    val isDone: Boolean = false,
    val leadId: UUID? = null,
    val userId: UUID? = null,
    val personId: UUID? = null,
    val organizationId: UUID? = null,
    val productId: UUID? = null,
    val warehouseId: UUID? = null,
)

data class ActivityParticipantResponse(
    val id: UUID,
    val userId: UUID?,
    val personId: UUID?,
)

data class ActivityResponse(
    val id: UUID,
    val title: String,
    val type: String,
    val comment: String?,
    val location: String?,
    val additional: String?,
    val isDone: Boolean,
    val scheduleFrom: Instant?,
    val scheduleTo: Instant?,
    val leadId: UUID?,
    val userId: UUID?,
    val personId: UUID?,
    val organizationId: UUID?,
    val productId: UUID?,
    val warehouseId: UUID?,
    /** Back-compat: just the user-typed participants. Prefer [participants]. */
    val participantIds: List<UUID>,
    /** Person-typed participants. Prefer [participants]. */
    val participantPersonIds: List<UUID>,
    val participants: List<ActivityParticipantResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/** Back-compat shim: defaults to a user participant when only `userId` is supplied. */
data class AddParticipantRequest(
    val userId: UUID,
)

data class AddUserParticipantRequest(
    val userId: UUID,
)

data class AddPersonParticipantRequest(
    val personId: UUID,
)

data class MassUpdateActivityRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot update more than $MAX_BATCH_SIZE activities at once")
    val ids: List<UUID>,
    val userId: UUID? = null,
    val isDone: Boolean? = null,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class MassDestroyActivityRequest(
    @field:Size(max = MAX_BATCH_SIZE, message = "Cannot delete more than $MAX_BATCH_SIZE activities at once")
    val ids: List<UUID>,
) {
    companion object {
        const val MAX_BATCH_SIZE = 500
    }
}

data class ActivityFileResponse(
    val id: UUID,
    val activityId: UUID,
    val name: String,
    val size: Long?,
    val contentType: String?,
    val createdAt: java.time.Instant?,
)

/** P3.6 — POST /api/activities/check-overlap body. */
data class CheckOverlapRequest(
    @field:NotNull val scheduleFrom: Instant,
    @field:NotNull val scheduleTo: Instant,
    @field:Size(max = 500, message = "Cannot check more than 500 user IDs at once")
    val userIds: List<UUID> = emptyList(),
    @field:Size(max = 500, message = "Cannot check more than 500 person IDs at once")
    val personIds: List<UUID> = emptyList(),
    /** Pass when editing an existing meeting so it doesn't overlap with itself. */
    val excludeActivityId: UUID? = null,
)

data class CheckOverlapResponse(
    val hasOverlap: Boolean,
    val overlaps: List<ActivityResponse>,
)
