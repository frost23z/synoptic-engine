package com.synopticengine.api.dashboard.web

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class StageStatsResponse(
    val stageId: UUID,
    val stageName: String,
    val count: Int,
    val value: BigDecimal,
)

data class SalespersonStatsResponse(
    val userId: UUID,
    val userName: String,
    val leadsWon: Int,
    val revenue: BigDecimal,
)

data class ActivitySummaryResponse(
    val id: UUID,
    val title: String,
    val type: String,
    val isDone: Boolean,
    val scheduleFrom: Instant?,
    val scheduleTo: Instant?,
)

data class DashboardResponse(
    val totalLeads: Int,
    val openLeads: Int,
    val wonLeads: Int,
    val lostLeads: Int,
    val totalRevenue: BigDecimal,
    val leadsByStage: List<StageStatsResponse>,
    val recentActivities: List<ActivitySummaryResponse>,
    val upcomingActivities: List<ActivitySummaryResponse>,
    val topSalespeople: List<SalespersonStatsResponse>,
)
