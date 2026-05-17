package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.Stage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StageRepository : JpaRepository<Stage, UUID> {
    fun findAllByPipelineIdAndDeletedAtIsNullOrderBySortOrderAsc(pipelineId: UUID): List<Stage>

    fun findByIdAndDeletedAtIsNull(id: UUID): Stage?

    fun existsByPipelineIdAndDeletedAtIsNull(pipelineId: UUID): Boolean

    fun findAllByIdIn(ids: Collection<UUID>): List<Stage>
}
