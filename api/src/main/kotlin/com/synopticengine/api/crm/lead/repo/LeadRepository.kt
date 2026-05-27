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
// T5.1 / T5.2 — see batch-fetch note in application.yaml and bulk-update methods below.

interface LeadRepository : JpaRepository<Lead, UUID> {
    @Query("SELECT l FROM Lead l LEFT JOIN FETCH l.tags WHERE l.id = :id AND l.deletedAt IS NULL")
    fun findActiveById(id: UUID): Lead?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Lead>

    // T5.1 — kanban path: LEFT JOIN FETCH tags so toResponse() doesn't trigger N+1.
    // Non-paginated list → no in-memory pagination risk.
    @Query(
        """
        SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.tags
        WHERE l.pipelineId = :pipelineId AND l.deletedAt IS NULL
    """,
    )
    fun findAllByPipelineIdAndDeletedAtIsNull(
        @Param("pipelineId") pipelineId: UUID,
    ): List<Lead>

    // T5.1 — scoped kanban path: same JOIN FETCH treatment.
    @Query(
        """
        SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.tags
        WHERE l.pipelineId = :pipelineId
        AND l.userId IN :scopeIds
        AND l.deletedAt IS NULL
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

    fun existsByPipelineIdAndDeletedAtIsNull(pipelineId: UUID): Boolean

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

    // ── Dashboard stats ─────────────────────────────────────────────────────────
    //
    // All native queries carry an explicit `tenant_id = :tenantId` predicate for
    // two reasons:
    //
    //  1. Defense-in-depth. RLS (V007/V011) is the primary isolation layer; the
    //     Hibernate `@Filter` is a second layer that the MVC interceptor and
    //     HibernateTenantFilterAspect maintain. Native SQL bypasses both the filter
    //     and query-rewriting, so we add an explicit WHERE clause as a third layer.
    //
    //  2. Integration-test fidelity. Tests run as the `synoptic_app` role with
    //     BYPASSRLS enabled in the test database, so RLS doesn't fire. Without
    //     explicit predicates, dashboard counts would merge across tenants in CI.
    //
    // T2.2 — explicit tenant predicate on dashboard native queries.

    @Query(
        value = """
            SELECT COUNT(*) FROM leads
            WHERE deleted_at IS NULL
              AND tenant_id = :tenantId
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
        """,
        nativeQuery = true,
    )
    fun countCreatedInRangeNative(
        @Param("tenantId") tenantId: UUID,
        @Param("start") start: Instant,
        @Param("end") end: Instant,
        @Param("hasScope") hasScope: Boolean,
        @Param("scopeIds") scopeIds: Collection<UUID>,
    ): Long

    @Query(
        value = """
            SELECT COALESCE(SUM(amount), 0) FROM leads
            WHERE deleted_at IS NULL
              AND tenant_id = :tenantId
              AND status = :status
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
        """,
        nativeQuery = true,
    )
    fun sumAmountByStatusInRangeNative(
        @Param("tenantId") tenantId: UUID,
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
              AND tenant_id = :tenantId
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY bucket_date
            ORDER BY bucket_date
        """,
        nativeQuery = true,
    )
    fun countCreatedByBucketNative(
        @Param("tenantId") tenantId: UUID,
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
              AND tenant_id = :tenantId
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
        @Param("tenantId") tenantId: UUID,
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
              AND tenant_id = :tenantId
              AND amount IS NOT NULL
              AND created_at >= :start AND created_at < :end
              AND (:hasScope = false OR user_id IN (:scopeIds))
        """,
        nativeQuery = true,
    )
    fun avgAmountInRangeNative(
        @Param("tenantId") tenantId: UUID,
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
              AND tenant_id = :tenantId
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
        @Param("tenantId") tenantId: UUID,
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
              AND tenant_id = :tenantId
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
        @Param("tenantId") tenantId: UUID,
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
            WHERE l.tenant_id = :tenantId
              AND l.status = 'won'
              AND l.created_at >= :start AND l.created_at < :end
              AND (:hasScope = false OR l.user_id IN (:scopeIds))
            GROUP BY lp.product_id, p.name, p.sku
            ORDER BY 3 DESC
            LIMIT :pageLimit
        """,
        nativeQuery = true,
    )
    fun topSellingProductsInRangeNative(
        @Param("tenantId") tenantId: UUID,
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
              AND tenant_id = :tenantId
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
        @Param("tenantId") tenantId: UUID,
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
              AND tenant_id = :tenantId
              AND status = 'open'
              AND (:hasScope = false OR user_id IN (:scopeIds))
            GROUP BY stage_id
        """,
        nativeQuery = true,
    )
    fun openLeadsByStageNative(
        @Param("tenantId") tenantId: UUID,
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

    @Modifying
    @Query(
        value = """
            INSERT INTO lead_tags (lead_id, tag_id)
            VALUES (:leadId, :tagId)
            ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun attachTag(
        @Param("leadId") leadId: UUID,
        @Param("tagId") tagId: UUID,
    ): Int

    // ── T5.2 Bulk mutations ─────────────────────────────────────────────────────
    //
    // Replace per-id find+save loops with single UPDATE statements so that mass
    // operations on 500 leads issue O(1) SQL rather than O(N) round-trips.

    /** Soft-delete all matching active leads in one shot. */
    @Modifying
    @Query("UPDATE Lead l SET l.deletedAt = :now WHERE l.id IN :ids AND l.deletedAt IS NULL")
    fun bulkSoftDelete(
        @Param("ids") ids: Collection<UUID>,
        @Param("now") now: Instant,
    ): Int

    /** Bulk-reassign the owning user. */
    @Modifying
    @Query("UPDATE Lead l SET l.userId = :userId WHERE l.id IN :ids AND l.deletedAt IS NULL")
    fun bulkSetUserId(
        @Param("ids") ids: Collection<UUID>,
        @Param("userId") userId: UUID,
    ): Int

    /**
     * Bulk-move to a new stage and stamp [stageUpdatedAt].
     * Only active leads are updated; caller must pre-compute which ones actually
     * change stage (via [findIdsWithDifferentStage]) to publish domain events.
     */
    @Modifying
    @Query(
        "UPDATE Lead l SET l.stageId = :stageId, l.stageUpdatedAt = :now " +
            "WHERE l.id IN :ids AND l.deletedAt IS NULL",
    )
    fun bulkSetStageId(
        @Param("ids") ids: Collection<UUID>,
        @Param("stageId") stageId: UUID,
        @Param("now") now: Instant,
    ): Int

    /** Bulk-set status field. */
    @Modifying
    @Query("UPDATE Lead l SET l.status = :status WHERE l.id IN :ids AND l.deletedAt IS NULL")
    fun bulkSetStatus(
        @Param("ids") ids: Collection<UUID>,
        @Param("status") status: LeadStatus,
    ): Int

    /**
     * Returns IDs from [ids] whose current stageId differs from [stageId].
     * Used by [massUpdate] to determine which leads will change stage so
     * [lead.stage.changed] events can be published accurately.
     */
    @Query(
        "SELECT l.id FROM Lead l " +
            "WHERE l.id IN :ids AND l.deletedAt IS NULL AND l.stageId <> :stageId",
    )
    fun findIdsWithDifferentStage(
        @Param("ids") ids: Collection<UUID>,
        @Param("stageId") stageId: UUID,
    ): List<UUID>

    /**
     * Returns (id, status) pairs for the given IDs.
     * Used to include the correct current status in stage-changed events when
     * massUpdate does not override status.
     */
    @Query("SELECT l.id, l.status FROM Lead l WHERE l.id IN :ids AND l.deletedAt IS NULL")
    fun findIdAndStatusByIds(
        @Param("ids") ids: Collection<UUID>,
    ): List<Array<Any>>

    /**
     * Bulk-reparent all active leads from one pipeline to another.
     * Used by [PipelineService.delete] instead of a load+loop.
     */
    @Modifying
    @Query(
        "UPDATE Lead l SET l.pipelineId = :toPipelineId, l.stageId = :toStageId " +
            "WHERE l.pipelineId = :fromPipelineId AND l.deletedAt IS NULL",
    )
    fun bulkReparentToDefaultPipeline(
        @Param("fromPipelineId") fromPipelineId: UUID,
        @Param("toPipelineId") toPipelineId: UUID,
        @Param("toStageId") toStageId: UUID,
    ): Int

    /**
     * Bulk-move all active leads in [pipelineId] from [fromStageId] to [toStageId].
     * Used by [PipelineService.deleteStage] instead of a load+loop.
     */
    @Modifying
    @Query(
        "UPDATE Lead l SET l.stageId = :toStageId " +
            "WHERE l.stageId = :fromStageId AND l.pipelineId = :pipelineId AND l.deletedAt IS NULL",
    )
    fun bulkReparentToStage(
        @Param("fromStageId") fromStageId: UUID,
        @Param("pipelineId") pipelineId: UUID,
        @Param("toStageId") toStageId: UUID,
    ): Int

    /**
     * Returns the subset of [personIds] that own at least one active lead.
     * Used by [PersonService.massDestroy] to skip persons that cannot be deleted.
     */
    @Query(
        "SELECT DISTINCT l.personId FROM Lead l " +
            "WHERE l.personId IN :personIds AND l.deletedAt IS NULL",
    )
    fun findPersonIdsHavingOpenLeads(
        @Param("personIds") personIds: Collection<UUID>,
    ): List<UUID>

    /**
     * Aggregate stage statistics: count + sum(amount) per stageId for non-deleted
     * leads belonging to the given tenant. Used by the dashboard to replace the OOM
     * `PageRequest.of(0, Int.MAX_VALUE)` load.
     * Returns rows of [stageId, count, sum(amount)].
     */
    @Query(
        value = """
            SELECT l.stage_id,
                   COUNT(*)                        AS lead_count,
                   COALESCE(SUM(l.amount), 0)      AS total_amount
            FROM leads l
            WHERE l.deleted_at IS NULL
              AND l.tenant_id = :tenantId
            GROUP BY l.stage_id
        """,
        nativeQuery = true,
    )
    fun countAndSumByStageNative(
        @Param("tenantId") tenantId: UUID,
    ): List<Array<Any>>
}
