package com.synopticengine.api.crm.datagrid.repo

import com.synopticengine.api.crm.datagrid.domain.DataGridSavedFilter
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DataGridSavedFilterRepository : JpaRepository<DataGridSavedFilter, UUID> {
    fun findAllByUserIdAndSrc(
        userId: UUID,
        src: String,
    ): List<DataGridSavedFilter>

    fun findAllByUserId(userId: UUID): List<DataGridSavedFilter>
}
