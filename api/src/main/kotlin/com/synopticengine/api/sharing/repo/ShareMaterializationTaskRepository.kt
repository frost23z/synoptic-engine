package com.synopticengine.api.sharing.repo

import com.synopticengine.api.sharing.domain.ShareMaterializationTask
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ShareMaterializationTaskRepository : JpaRepository<ShareMaterializationTask, UUID> {
    @Query(
        "SELECT t FROM ShareMaterializationTask t WHERE t.finishedAt IS NULL ORDER BY t.enqueuedAt ASC",
    )
    fun findPending(): List<ShareMaterializationTask>
}
