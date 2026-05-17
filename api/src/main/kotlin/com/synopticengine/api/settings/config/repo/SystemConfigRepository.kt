package com.synopticengine.api.settings.config.repo

import com.synopticengine.api.settings.config.domain.SystemConfig
import org.springframework.data.jpa.repository.JpaRepository

interface SystemConfigRepository : JpaRepository<SystemConfig, String> {
    fun findAllByOrderByGroupNameAscSortOrderAsc(): List<SystemConfig>
}
