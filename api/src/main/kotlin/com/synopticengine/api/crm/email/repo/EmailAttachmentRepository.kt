package com.synopticengine.api.crm.email.repo

import com.synopticengine.api.crm.email.domain.EmailAttachment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EmailAttachmentRepository : JpaRepository<EmailAttachment, UUID> {
    // Tenant-aware load — see EmailRepository.findActiveById docstring.
    @Query("SELECT a FROM EmailAttachment a WHERE a.id = :id AND a.deletedAt IS NULL")
    fun findActiveById(
        @Param("id") id: UUID,
    ): EmailAttachment?
}
