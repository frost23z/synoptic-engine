package com.synopticengine.api.settings.emailtemplate.repo

import com.synopticengine.api.settings.emailtemplate.domain.EmailTemplate
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmailTemplateRepository : JpaRepository<EmailTemplate, UUID> {
    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(
        name: String,
        id: UUID,
    ): Boolean
}
