package com.synopticengine.api.crm.activity.repo

import com.synopticengine.api.crm.activity.domain.ActivityFile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ActivityFileRepository : JpaRepository<ActivityFile, UUID> {
    fun findAllByActivityId(activityId: UUID): List<ActivityFile>
}
