package com.synopticengine.api.settings.marketing.repo

import com.synopticengine.api.settings.marketing.domain.MarketingCampaign
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MarketingCampaignRepository : JpaRepository<MarketingCampaign, UUID>
