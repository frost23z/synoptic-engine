package com.synopticengine.api.settings.automation.repo

import com.synopticengine.api.settings.automation.domain.Workflow
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowRepository : JpaRepository<Workflow, UUID> {
    fun findByEventNameAndIsActiveTrue(eventName: String): List<Workflow>
}
