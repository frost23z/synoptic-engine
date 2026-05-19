package com.synopticengine.api.settings.automation.domain

import com.synopticengine.api.shared.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.Filter
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

/**
 * Phase 3 / P3.2 — observability for the workflow action engine.
 *
 * One row per *action attempt*, even when the workflow's condition gate
 * filtered it out. `status = SKIPPED` carries that case so the UI can
 * answer "did this lead trigger any workflow?" without having to re-evaluate.
 */
@Entity
@Table(name = "workflow_action_runs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
class WorkflowActionRun : BaseEntity() {
    @Column(nullable = false)
    var workflowId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var eventName: String = ""

    @Column(nullable = false)
    var entityType: String = ""

    @Column(nullable = false)
    var entityId: UUID = UUID.randomUUID()

    @Column(nullable = false)
    var actionType: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WorkflowActionRunStatus = WorkflowActionRunStatus.SUCCESS

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var payload: Map<String, Any?>? = null
}

enum class WorkflowActionRunStatus { SUCCESS, FAILED, SKIPPED }
