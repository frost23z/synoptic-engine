package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.Lead
import com.synopticengine.api.crm.lead.domain.LeadStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface LeadRepository : JpaRepository<Lead, UUID> {
    @Query("SELECT l FROM Lead l LEFT JOIN FETCH l.tags WHERE l.id = :id AND l.deletedAt IS NULL")
    fun findActiveById(id: UUID): Lead?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Lead>

    fun findAllByPipelineIdAndDeletedAtIsNull(pipelineId: UUID): List<Lead>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
        AND l.pipelineId = :pipelineId
        AND l.userId IN :scopeIds
    """,
    )
    fun findAllByPipelineIdScopedAndDeletedAtIsNull(
        @Param("pipelineId") pipelineId: UUID,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): List<Lead>

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

    @Modifying
    @Query(
        """
        UPDATE Lead l
        SET l.personId = :targetPersonId
        WHERE l.personId = :sourcePersonId
          AND l.deletedAt IS NULL
    """,
    )
    fun reassignPerson(
        @Param("sourcePersonId") sourcePersonId: UUID,
        @Param("targetPersonId") targetPersonId: UUID,
    ): Int

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

    // ── Dashboard stats (Phase 3 / P3.1) ──────────────────────────────────────
    //
    // Each accepts an optional `scopeIds` (null = unrestricted view); the SQL
    // uses `:scopeIds IS NULL OR l.user_id IN (:scopeIds)` so we can keep a
    // single query that works for both GLOBAL viewers and scoped users.
    //
    // Native SQL is used here because we want DATE_TRUNC for bucketing and
    // because the dashboard queries dominate the API's read traffic — staying
    // close to PG means we get the composite indexes defined in V043 directly.

    @Query(
        value = """
            SELECT COUNT(*) FROM leads
            WHERE deleted_at IS NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
        """,
        nativeQuery = true,
    )
    fun countCreatedInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): Long

    @Query(
        value = """
            SELECT COALESCE(SUM(amount), 0) FROM leads
            WHERE deleted_at IS NULL
              AND status = :status
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
        """,
        nativeQuery = true,
    )
    fun sumAmountByStatusInRangeNative(
        @Param("status") status: String,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): BigDecimal

    @Query(
        value = """
            SELECT DATE_TRUNC(:bucket, created_at)::date AS bucket_date, COUNT(*) AS cnt
            FROM leads
            WHERE deleted_at IS NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY bucket_date
            ORDER BY bucket_date
        """,
        nativeQuery = true,
    )
    fun countCreatedByBucketNative(
        @Param("bucket") bucket: String,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT DATE_TRUNC(:bucket, closed_at)::date AS bucket_date, COUNT(*) AS cnt
            FROM leads
            WHERE deleted_at IS NULL
              AND status = :status
              AND closed_at IS NOT NULL
              AND closed_at >= :start AND closed_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY bucket_date
            ORDER BY bucket_date
        """,
        nativeQuery = true,
    )
    fun countStatusByBucketNative(
        @Param("bucket") bucket: String,
        @Param("status") status: String,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT COALESCE(AVG(amount), 0) FROM leads
            WHERE deleted_at IS NULL
              AND amount IS NOT NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
        """,
        nativeQuery = true,
    )
    fun avgAmountInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): BigDecimal

    @Query(
        value = """
            SELECT lead_source_id, COALESCE(SUM(amount), 0), COUNT(*)
            FROM leads
            WHERE deleted_at IS NULL
              AND status = 'won'
              AND lead_source_id IS NOT NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY lead_source_id
            ORDER BY 2 DESC
        """,
        nativeQuery = true,
    )
    fun revenueByLeadSourceInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT lead_type_id, COALESCE(SUM(amount), 0), COUNT(*)
            FROM leads
            WHERE deleted_at IS NULL
              AND status = 'won'
              AND lead_type_id IS NOT NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY lead_type_id
            ORDER BY 2 DESC
        """,
        nativeQuery = true,
    )
    fun revenueByLeadTypeInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT lp.product_id, SUM(lp.quantity), COALESCE(SUM(lp.quantity * COALESCE(lp.unit_price, p.price)), 0),
                   COALESCE(p.name, 'Unknown'), p.sku
            FROM lead_products lp
            JOIN leads l ON l.id = lp.lead_id AND l.deleted_at IS NULL
            LEFT JOIN products p ON p.id = lp.product_id AND p.deleted_at IS NULL
            WHERE l.status = 'won'
              AND l.created_at >= :start AND l.created_at < :end
              AND (:hasScope = false OR l.user_id IN (:scopeIds))
            GROUP BY lp.product_id, p.name, p.sku
            ORDER BY 3 DESC
            LIMIT :pageLimit
        """,
        nativeQuery = true,
    )
    fun topSellingProductsInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
        @Param("pageLimit") pageLimit: Int,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT person_id, COUNT(*), COALESCE(SUM(amount), 0)
            FROM leads
            WHERE deleted_at IS NULL
              AND status = 'won'
              AND person_id IS NOT NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY person_id
            ORDER BY 3 DESC
            LIMIT :pageLimit
        """,
        nativeQuery = true,
    )
    fun topPersonsByRevenueInRangeNative(
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
        @Param("pageLimit") pageLimit: Int,
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT stage_id, COUNT(*), COALESCE(SUM(amount), 0)
            FROM leads
            WHERE deleted_at IS NULL
              AND status = 'open'
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY stage_id
        """,
        nativeQuery = true,
    )
    fun openLeadsByStageNative(
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): List<Array<Any>>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
          AND l.status = com.synopticengine.api.crm.lead.domain.LeadStatus.OPEN
          AND (:pipelineId IS NULL OR l.pipelineId = :pipelineId)
    """,
    )
    fun findOpenForRotten(
        @Param("pipelineId") pipelineId: UUID?,
    ): List<Lead>

    @Query(
        """
        SELECT l FROM Lead l
        WHERE l.deletedAt IS NULL
          AND l.status = com.synopticengine.api.crm.lead.domain.LeadStatus.OPEN
          AND (:pipelineId IS NULL OR l.pipelineId = :pipelineId)
          AND l.userId IN :scopeIds
    """,
    )
    fun findOpenForRottenScoped(
        @Param("pipelineId") pipelineId: UUID?,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): List<Lead>
}
