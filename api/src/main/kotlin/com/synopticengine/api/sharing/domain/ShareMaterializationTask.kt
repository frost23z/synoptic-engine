package com.synopticengine.api.sharing.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Background work item: materialize / unmaterialize the [ResourceVisibility] rows for a
 * given policy. Created when a [TenantSharePolicy] is inserted/updated/revoked;
 * processed by `ShareMaterializationWorker`.
 */
@Entity
@Table(name = "share_materialization_queue")
@EntityListeners(AuditingEntityListener::class)
class ShareMaterializationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null

    @Column(name = "policy_id", nullable = false, columnDefinition = "uuid")
    var policyId: UUID = UUID(0, 0)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var op: ShareMaterializationOp = ShareMaterializationOp.INSERT

    @CreatedDate
    @Column(name = "enqueued_at", nullable = false, updatable = false)
    var enqueuedAt: Instant? = null
        protected set

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "finished_at")
    var finishedAt: Instant? = null

    @Column(columnDefinition = "TEXT")
    var error: String? = null
}
