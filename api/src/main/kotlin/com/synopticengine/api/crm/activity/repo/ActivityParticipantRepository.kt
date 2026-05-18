package com.synopticengine.api.crm.activity.repo

import com.synopticengine.api.crm.activity.domain.ActivityParticipant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
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
    @Query("DELETE FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.userId = :userId")
    fun deleteByActivityIdAndUserId(
        activityId: UUID,
        userId: UUID,
    )

    @Modifying
    @Query("DELETE FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.personId = :personId")
    fun deleteByActivityIdAndPersonId(
        activityId: UUID,
        personId: UUID,
    )

    @Modifying
    @Query("DELETE FROM ActivityParticipant p WHERE p.id = :id")
    fun deleteByParticipantId(id: UUID)
}
