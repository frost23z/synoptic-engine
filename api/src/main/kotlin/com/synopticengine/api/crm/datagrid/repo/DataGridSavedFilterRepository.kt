package com.synopticengine.api.crm.datagrid.repo

import com.synopticengine.api.crm.datagrid.domain.DataGridSavedFilter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface DataGridSavedFilterRepository : JpaRepository<DataGridSavedFilter, UUID> {
    fun findAllByUserIdAndSrc(
        userId: UUID,
        src: String,
    ): List<DataGridSavedFilter>

    fun findAllByUserId(userId: UUID): List<DataGridSavedFilter>

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT f FROM DataGridSavedFilter f WHERE f.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): DataGridSavedFilter?
}
