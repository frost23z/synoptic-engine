package com.synopticengine.api.crm.email.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.util.UUID

@Entity
@Table(name = "email_attachments")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class EmailAttachment : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id", nullable = false)
    lateinit var email: Email

    @Column(name = "email_id", insertable = false, updatable = false)
    var emailId: UUID = UUID.randomUUID()

    @Column(name = "attachment_path", nullable = false, length = 1000)
    var attachmentPath: String = ""

    @Column(name = "attachment_filename", nullable = false, length = 500)
    var attachmentFilename: String = ""

    @Column(name = "content_type")
    var contentType: String? = null

    @Column
    var size: Long? = null
}
