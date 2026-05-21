package com.synopticengine.api.crm.quote.repo

import com.synopticengine.api.crm.quote.domain.Quote
import com.synopticengine.api.crm.quote.domain.QuoteStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface QuoteRepository : JpaRepository<Quote, UUID> {
    @Query("SELECT q FROM Quote q LEFT JOIN FETCH q.items WHERE q.id = :id AND q.deletedAt IS NULL")
    fun findActiveById(id: UUID): Quote?

    @Query(
        """
        SELECT q FROM Quote q
        WHERE q.deletedAt IS NULL
        AND (:leadId IS NULL OR q.leadId = :leadId)
        AND (:status IS NULL OR q.status = :status)
    """,
    )
    fun filter(
        leadId: UUID?,
        status: QuoteStatus?,
        pageable: Pageable,
    ): Page<Quote>

    @Query(
        """
        SELECT q FROM Quote q
        WHERE q.deletedAt IS NULL
        AND (:leadId IS NULL OR q.leadId = :leadId)
        AND (:status IS NULL OR q.status = :status)
        AND q.userId IN :scopeIds
    """,
    )
    fun filterScoped(
        leadId: UUID?,
        status: QuoteStatus?,
        scopeIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Quote>

    @Query(
        """
        SELECT q FROM Quote q
        WHERE q.deletedAt IS NULL
          AND q.expiredAt IS NOT NULL
          AND q.expiredAt < :today
    """,
    )
    fun findExpired(
        today: LocalDate,
        pageable: Pageable,
    ): Page<Quote>

    @Query(
        """
        SELECT q FROM Quote q
        WHERE q.deletedAt IS NULL
          AND q.expiredAt IS NOT NULL
          AND q.expiredAt < :today
          AND q.userId IN :scopeIds
    """,
    )
    fun findExpiredScoped(
        today: LocalDate,
        scopeIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Quote>

    @Query(
        """
        SELECT q FROM Quote q
        WHERE q.deletedAt IS NULL
          AND LOWER(q.title) LIKE LOWER(CONCAT('%', :q, '%'))
    """,
    )
    fun search(
        q: String,
        pageable: Pageable,
    ): Page<Quote>

    @Query(
        """
        SELECT q FROM Quote q
        WHERE q.deletedAt IS NULL
          AND LOWER(q.title) LIKE LOWER(CONCAT('%', :q, '%'))
          AND q.userId IN :scopeIds
    """,
    )
    fun searchScoped(
        q: String,
        scopeIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Quote>

    // Native query — Hibernate `@Filter("tenantFilter")` does NOT rewrite native
    // SQL, and the `quotes` table has no Postgres RLS policy. The tenant_id
    // predicate here is the only isolation layer; callers must pass
    // TenantContext.get().
    @Query(
        value = """
            SELECT COUNT(*) FROM quotes
            WHERE tenant_id = :tenantId
              AND deleted_at IS NULL
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
}
