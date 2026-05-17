package com.synopticengine.api.crm.email.repo

import com.synopticengine.api.crm.email.domain.EmailAttachment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmailAttachmentRepository : JpaRepository<EmailAttachment, UUID>
