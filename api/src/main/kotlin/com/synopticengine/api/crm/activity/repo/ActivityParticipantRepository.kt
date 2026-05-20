package com.synopticengine.api.crm.activity.repo

import com.synopticengine.api.crm.activity.domain.ActivityParticipant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import java.util.UUID

interface ActivityParticipantRepository : JpaRepository<ActivityParticipant, UUID> {
    fun findAllByActivityId(activityId: UUID): List<ActivityParticipant>

    fun findByActivityIdAndUserId(
        activityId: UUID,
        userId: UUID,
    ): ActivityParticipant?

    fun findByActivityIdAndPersonId(
        activityId: UUID,
        personId: UUID,
    ): ActivityParticipant?

    @Modifying
    fun deleteByActivityIdAndUserId(
        activityId: UUID,
        userId: UUID,
    )

    @Modifying
    fun deleteByActivityIdAndPersonId(
        activityId: UUID,
        personId: UUID,
    )
}
