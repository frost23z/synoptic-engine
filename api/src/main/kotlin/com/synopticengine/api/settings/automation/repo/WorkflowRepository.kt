package com.synopticengine.api.settings.automation.repo

import com.synopticengine.api.settings.automation.domain.Workflow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WorkflowRepository : JpaRepository<Workflow, UUID> {
    fun findByEventNameAndIsActiveTrue(eventName: String): List<Workflow>

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT w FROM Workflow w WHERE w.id = :id AND w.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): Workflow?
}
