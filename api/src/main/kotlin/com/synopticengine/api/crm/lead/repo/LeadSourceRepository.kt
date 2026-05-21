package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.LeadSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LeadSourceRepository : JpaRepository<LeadSource, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT s FROM LeadSource s WHERE s.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): LeadSource?
}
