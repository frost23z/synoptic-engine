package com.synopticengine.api.crm.email.domain

import com.synopticengine.api.crm.tag.domain.Tag
import com.synopticengine.api.shared.domain.BaseEntity
import com.synopticengine.api.shared.domain.SoftDeletable
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "emails")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@SQLDelete(sql = "UPDATE emails SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
class Email :
    BaseEntity(),
    SoftDeletable {
    @Column
    var subject: String? = null

    @Column
    var source: String? = null

    @Column
    var name: String? = null

    @Column(name = "user_type")
    var userType: String? = null

    @Column(nullable = false)
    var isRead: Boolean = false

    /** P3.3: DRAFT until explicitly sent; SENT for outbound/inbound that arrived. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EmailStatus = EmailStatus.SENT

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var folders: List<String> = listOf("inbox")

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"from\"", columnDefinition = "jsonb")
    var from: Map<String, String>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    var sender: Map<String, String>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reply_to", columnDefinition = "jsonb")
    var replyTo: List<Map<String, String>>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var cc: List<Map<String, String>>? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var bcc: List<Map<String, String>>? = null

    @Column(columnDefinition = "TEXT")
    var body: String? = null

    @Column(columnDefinition = "TEXT")
    var reply: String? = null

    @Column(name = "unique_id")
    var uniqueId: String? = null

    @Column(name = "message_id")
    var messageId: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_ids", columnDefinition = "jsonb")
    var referenceIds: List<String>? = null

    @Column(name = "person_id")
    var personId: UUID? = null

    @Column(name = "parent_id")
    var parentId: UUID? = null

    @Column(name = "lead_id")
    var leadId: UUID? = null

    @Column
    override var deletedAt: Instant? = null

    @OneToMany(mappedBy = "email", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    val attachments: MutableList<EmailAttachment> = mutableListOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "email_tags",
        joinColumns = [JoinColumn(name = "email_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")],
    )
    val tags: MutableSet<Tag> = mutableSetOf()
}
