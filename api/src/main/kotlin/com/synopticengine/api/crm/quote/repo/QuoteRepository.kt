package com.synopticengine.api.crm.quote.repo

import com.synopticengine.api.crm.quote.domain.Quote
import com.synopticengine.api.crm.quote.domain.QuoteStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}
