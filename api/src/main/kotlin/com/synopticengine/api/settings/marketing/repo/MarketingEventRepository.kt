package com.synopticengine.api.settings.marketing.repo

import com.synopticengine.api.settings.marketing.domain.MarketingEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MarketingEventRepository : JpaRepository<MarketingEvent, UUID> {
    @Query("SELECT e FROM MarketingEvent e WHERE e.id = :id AND e.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): MarketingEvent?
}
