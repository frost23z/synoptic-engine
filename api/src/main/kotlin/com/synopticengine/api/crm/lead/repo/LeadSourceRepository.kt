package com.synopticengine.api.crm.lead.repo

import com.synopticengine.api.crm.lead.domain.LeadSource
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LeadSourceRepository : JpaRepository<LeadSource, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean
}
