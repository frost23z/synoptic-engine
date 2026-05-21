package com.synopticengine.api.settings.imports.repo

import com.synopticengine.api.settings.imports.domain.DataImport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface DataImportRepository : JpaRepository<DataImport, UUID> {
    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT d FROM DataImport d WHERE d.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): DataImport?
}
