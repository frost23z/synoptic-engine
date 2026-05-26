package com.synopticengine.api.crm.activity.repo

import com.synopticengine.api.crm.activity.domain.ActivityFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ActivityFileRepository : JpaRepository<ActivityFile, UUID> {
    fun findAllByActivityId(activityId: UUID): List<ActivityFile>

    @Query("SELECT f FROM ActivityFile f WHERE f.id = :id AND f.activityId = :activityId")
    fun findByIdAndActivityId(
        @Param("id") id: UUID,
        @Param("activityId") activityId: UUID,
    ): ActivityFile?
}
