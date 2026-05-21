package com.synopticengine.api.settings.emailtemplate.repo

import com.synopticengine.api.settings.emailtemplate.domain.EmailTemplate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EmailTemplateRepository : JpaRepository<EmailTemplate, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean

    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT t FROM EmailTemplate t WHERE t.id = :id AND t.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): EmailTemplate?
}
