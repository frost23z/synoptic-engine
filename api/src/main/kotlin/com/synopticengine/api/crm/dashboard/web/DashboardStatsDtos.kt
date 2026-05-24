package com.synopticengine.api.crm.dashboard.web

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Counts with period-over-period delta. */
data class PeriodCount(
    val current: Int,
    val previous: Int,
    val delta: Int,
    val changePercent: BigDecimal,
)

data class OverAllStatsResponse(
    val leads: PeriodCount,
    val averageLeadValue: PeriodValue,
    val averageLeadsPerDay: PeriodValue,
    val activities: PeriodCount,
    val quotes: PeriodCount,
    val persons: PeriodCount,
    val organizations: PeriodCount,
)

data class PeriodValue(
    val current: BigDecimal,
    val previous: BigDecimal,
    val delta: BigDecimal,
    val changePercent: BigDecimal,
)

data class RevenueStatsResponse(
    val wonRevenue: BigDecimal,
    val lostRevenue: BigDecimal,
    val previousWonRevenue: BigDecimal,
    val previousLostRevenue: BigDecimal,
)

data class TimeSeriesBucket(
    val date: LocalDate,
    val count: Int,
)

data class TotalLeadsSeries(
    val overTime: List<TimeSeriesBucket>,
)

data class TotalLeadsResponse(
    val bucket: String,
    val series: List<TimeSeriesBucket>,
    val all: TotalLeadsSeries,
    val won: TotalLeadsSeries,
    val lost: TotalLeadsSeries,
)

data class RevenueByDimensionEntry(
    val id: UUID,
    val name: String,
    val revenue: BigDecimal,
    val count: Int,
)

data class TopProductEntry(
    val productId: UUID,
    val productName: String,
    val sku: String?,
    val totalQuantity: Int,
    val totalRevenue: BigDecimal,
)

data class TopPersonEntry(
    val personId: UUID,
    val name: String,
    val wonLeads: Int,
    val revenue: BigDecimal,
)

data class OpenLeadsByStateEntry(
    val stageId: UUID,
    val stageName: String,
    val stageColor: String?,
    val count: Int,
    val value: BigDecimal,
)
