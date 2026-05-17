package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.LeadType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LeadTypeRepository : JpaRepository<LeadType, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean
}
