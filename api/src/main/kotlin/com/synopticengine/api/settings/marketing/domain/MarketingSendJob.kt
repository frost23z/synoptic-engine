package com.synopticengine.api.settings.marketing.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "marketing_send_jobs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class MarketingSendJob : BaseEntity() {
    @Column(name = "campaign_id", nullable = false)
    var campaignId: UUID = UUID.randomUUID()

    @Column(nullable = false, length = 320)
    var recipient: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SendJobStatus = SendJobStatus.PENDING

    @Column(nullable = false)
    var attemptCount: Int = 0

    @Column(nullable = false)
    var nextAttemptAt: Instant = Instant.now()

    @Column
    var sentAt: Instant? = null

    @Column
    var failedAt: Instant? = null

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null

    @Column(columnDefinition = "TEXT", nullable = false)
    var subject: String = ""

    @Column(columnDefinition = "TEXT", nullable = false)
    var body: String = ""
}
