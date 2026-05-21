package com.synopticengine.api.settings.marketing.repo

import com.synopticengine.api.settings.marketing.domain.MarketingCampaign
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MarketingCampaignRepository : JpaRepository<MarketingCampaign, UUID> {
    @Query("SELECT c FROM MarketingCampaign c WHERE c.id = :id AND c.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): MarketingCampaign?

    @Query("SELECT COUNT(c) > 0 FROM MarketingCampaign c WHERE c.eventId = :eventId AND c.deletedAt IS NULL")
    fun existsActiveByEventId(
        @Param("eventId") eventId: UUID,
    ): Boolean
}
