package com.synopticengine.api.settings.marketing.repo

import com.synopticengine.api.settings.marketing.domain.MarketingEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MarketingEventRepository : JpaRepository<MarketingEvent, UUID>
