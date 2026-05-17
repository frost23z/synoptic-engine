package com.synopticengine.api.dashboard

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.identity.IdentityApi
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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

@RestController
@RequestMapping($$"${api.base-path}/dashboard")
class DashboardController(
    private val crmApi: CrmApi,
    private val identityApi: IdentityApi,
) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getDashboard(): ResponseEntity<DashboardResponse> {
        val stats = crmApi.getDashboardLeadStats()
        val recentActivities = crmApi.getRecentActivities(10)
        val upcomingActivities = crmApi.getUpcomingActivities(10)

        val topSalespeople =
            stats.topSalespeople.map { s ->
                val userName = identityApi.findById(s.userId)?.fullName ?: "Unknown"
                SalespersonStatsResponse(
                    userId = s.userId,
                    userName = userName,
                    leadsWon = s.leadsWon,
                    revenue = s.revenue,
                )
            }

        return ResponseEntity.ok(
            DashboardResponse(
                totalLeads = stats.totalLeads,
                openLeads = stats.openLeads,
                wonLeads = stats.wonLeads,
                lostLeads = stats.lostLeads,
                totalRevenue = stats.totalRevenue,
                leadsByStage =
                    stats.leadsByStage.map {
                        StageStatsResponse(it.stageId, it.stageName, it.count, it.value)
                    },
                recentActivities =
                    recentActivities.map {
                        ActivitySummaryResponse(it.id, it.title, it.type, it.isDone, it.scheduleFrom, it.scheduleTo)
                    },
                upcomingActivities =
                    upcomingActivities.map {
                        ActivitySummaryResponse(it.id, it.title, it.type, it.isDone, it.scheduleFrom, it.scheduleTo)
                    },
                topSalespeople = topSalespeople,
            ),
        )
    }
}
