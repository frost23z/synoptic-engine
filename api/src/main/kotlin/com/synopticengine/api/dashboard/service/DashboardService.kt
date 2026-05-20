package com.synopticengine.api.dashboard.service

import com.synopticengine.api.crm.ActivitySummary
import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.dashboard.web.ActivitySummaryResponse
import com.synopticengine.api.dashboard.web.DashboardResponse
import com.synopticengine.api.dashboard.web.SalespersonStatsResponse
import com.synopticengine.api.dashboard.web.StageStatsResponse
import com.synopticengine.api.identity.IdentityApi
import org.springframework.stereotype.Service

/**
 * Composes the homepage summary from CRM stats and identity lookups. Lives as its own
 * service so the controller stays thin and the composition rules (e.g. activity
 * windows, salesperson name resolution) are testable in isolation.
 */
@Service
class DashboardService(
    private val crmApi: CrmApi,
    private val identityApi: IdentityApi,
) {
    fun buildSummary(): DashboardResponse {
        val stats = crmApi.getDashboardLeadStats()
        val recent = crmApi.getRecentActivities(RECENT_ACTIVITY_LIMIT)
        val upcoming = crmApi.getUpcomingActivities(UPCOMING_ACTIVITY_LIMIT)
        val topSalespeople =
            stats.topSalespeople.map { s ->
                SalespersonStatsResponse(
                    userId = s.userId,
                    userName = identityApi.findById(s.userId)?.fullName ?: "Unknown",
                    leadsWon = s.leadsWon,
                    revenue = s.revenue,
                )
            }
        return DashboardResponse(
            totalLeads = stats.totalLeads,
            openLeads = stats.openLeads,
            wonLeads = stats.wonLeads,
            lostLeads = stats.lostLeads,
            totalRevenue = stats.totalRevenue,
            leadsByStage = stats.leadsByStage.map { StageStatsResponse(it.stageId, it.stageName, it.count, it.value) },
            recentActivities = recent.map { it.toSummary() },
            upcomingActivities = upcoming.map { it.toSummary() },
            topSalespeople = topSalespeople,
        )
    }

    private fun ActivitySummary.toSummary() = ActivitySummaryResponse(id, title, type, isDone, scheduleFrom, scheduleTo)

    companion object {
        private const val RECENT_ACTIVITY_LIMIT = 10
        private const val UPCOMING_ACTIVITY_LIMIT = 10
    }
}
