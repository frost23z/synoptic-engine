package com.synopticengine.api.crm.activity.web

import com.synopticengine.api.crm.activity.domain.ActivityType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
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
    val ids: List<UUID>,
    val userId: UUID? = null,
    val isDone: Boolean? = null,
)

data class MassDestroyActivityRequest(
    val ids: List<UUID>,
)

data class ActivityFileResponse(
    val id: UUID,
    val activityId: UUID,
    val name: String,
    val size: Long?,
    val contentType: String?,
    val createdAt: java.time.Instant?,
)
