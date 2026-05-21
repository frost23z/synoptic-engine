package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.LeadType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LeadTypeRepository : JpaRepository<LeadType, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT t FROM LeadType t WHERE t.id = :id")
    fun findActiveById(
        @Param("id") id: UUID,
    ): LeadType?
}
