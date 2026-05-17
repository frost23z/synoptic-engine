package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.domain.LeadStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface LeadRepository : JpaRepository<Lead, UUID> {
    @Query("SELECT l FROM Lead l LEFT JOIN FETCH l.tags WHERE l.id = :id AND l.deletedAt IS NULL")
    fun findActiveById(id: UUID): Lead?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Lead>

    fun findAllByPipelineIdAndDeletedAtIsNull(pipelineId: UUID): List<Lead>

    fun findAllByPipelineIdAndStageIdAndDeletedAtIsNull(
        pipelineId: UUID,
        stageId: UUID,
    ): List<Lead>

    fun findAllByUserIdAndDeletedAtIsNull(
        userId: UUID,
        pageable: Pageable,
    ): Page<Lead>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
        AND l.pipelineId = :pipelineId
        AND (:stageId IS NULL OR l.stageId = :stageId)
        AND (:status IS NULL OR l.status = :status)
        AND (:userId IS NULL OR l.userId = :userId)
    """,
    )
    fun filter(
        pipelineId: UUID,
        stageId: UUID?,
        status: LeadStatus?,
        userId: UUID?,
        pageable: Pageable,
    ): Page<Lead>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
        AND l.pipelineId = :pipelineId
        AND (:stageId IS NULL OR l.stageId = :stageId)
        AND (:status IS NULL OR l.status = :status)
        AND (:userId IS NULL OR l.userId = :userId)
        AND l.userId IN :scopeIds
    """,
    )
    fun filterScoped(
        pipelineId: UUID,
        stageId: UUID?,
        status: LeadStatus?,
        userId: UUID?,
        scopeIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Lead>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
        AND l.userId IN :scopeIds
    """,
    )
    fun findAllScoped(
        scopeIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Lead>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
        AND (LOWER(l.title)       LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(l.description) LIKE LOWER(CONCAT('%', :q, '%')))
    """,
    )
    fun search(
        q: String,
        pageable: Pageable,
    ): Page<Lead>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
        AND (LOWER(l.title)       LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(l.description) LIKE LOWER(CONCAT('%', :q, '%')))
        AND l.userId IN :scopeIds
    """,
    )
    fun searchScoped(
        q: String,
        scopeIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Lead>

    fun countByDeletedAtIsNull(): Int

    fun existsByPersonIdAndDeletedAtIsNull(personId: UUID): Boolean

    fun countByStatusAndDeletedAtIsNull(status: LeadStatus): Int

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM Lead l WHERE l.status = :status AND l.deletedAt IS NULL")
    fun sumAmountByStatus(status: LeadStatus): BigDecimal

    @Query(
        """
        SELECT l.userId, COUNT(l), COALESCE(SUM(l.amount), 0)
        FROM Lead l
        WHERE l.status = com.synopticengine.api.crm.lead.domain.LeadStatus.WON
          AND l.deletedAt IS NULL
          AND l.userId IS NOT NULL
        GROUP BY l.userId
        ORDER BY COUNT(l) DESC
    """,
    )
    fun findTopSalespeople(pageable: Pageable): List<Array<Any>>
}
