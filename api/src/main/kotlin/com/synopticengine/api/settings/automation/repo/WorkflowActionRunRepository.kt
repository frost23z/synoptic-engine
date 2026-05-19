package com.synopticengine.api.settings.automation.repo

import com.synopticengine.api.settings.automation.domain.WorkflowActionRun
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowActionRunRepository : JpaRepository<WorkflowActionRun, UUID> {
    fun findAllByWorkflowIdOrderByCreatedAtDesc(
        workflowId: UUID,
        pageable: Pageable,
    ): Page<WorkflowActionRun>

    fun findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        entityType: String,
        entityId: UUID,
        pageable: Pageable,
    ): Page<WorkflowActionRun>
}
