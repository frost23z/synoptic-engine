package com.synopticengine.api.crm.dashboard

import com.synopticengine.api.crm.dashboard.service.DashboardStatsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

/**
 * Phase 3 / P3.1 — Krayin-parity dashboard stats.
 *
 * Single endpoint dispatching on `type=` so the frontend can issue eight tiny
 * fetches in parallel. All endpoints are scoped through [DashboardStatsService],
 * which threads [com.synopticengine.api.crm.scoping.ScopeResolver] through each
 * query.
 */
@RestController
@RequestMapping($$"${api.base-path}/dashboard/stats")
class DashboardStatsController(
    private val statsService: DashboardStatsService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('reports.view')")
    fun stats(
        @RequestParam type: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?,
        @RequestParam(required = false, defaultValue = "day") bucket: String,
    ): ResponseEntity<Any> {
        val range = resolveRange(startDate, endDate)
        val response: Any =
            when (type) {
                "over-all" -> statsService.overAll(range)
                "revenue-stats" -> statsService.revenueStats(range)
                "total-leads" -> statsService.totalLeads(range, bucket)
                "revenue-by-sources" -> statsService.revenueBySources(range)
                "revenue-by-types" -> statsService.revenueByTypes(range)
                "top-selling-products" -> statsService.topSellingProducts(range, limit = 10)
                "top-persons" -> statsService.topPersons(range, limit = 10)
                "open-leads-by-states" -> statsService.openLeadsByStates()
                else -> throw IllegalArgumentException("Unknown stats type: $type")
            }
        return ResponseEntity.ok(response)
    }

    private fun resolveRange(
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): DateRange {
        val end = endDate ?: LocalDate.now()
        val start = startDate ?: end.minusDays(30)
        if (end.isBefore(start)) {
            throw IllegalArgumentException("endDate must be on or after startDate")
        }
        return DateRange(
            start = start,
            end = end,
            startInstant = start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            endInstant = end.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
        )
    }
}

data class DateRange(
    val start: LocalDate,
    val end: LocalDate,
    val startInstant: Instant,
    val endInstant: Instant,
) {
    /** Same length but ending right before [start] — used for period-over-period change. */
    val previousStartInstant: Instant
        get() {
            val len = java.time.Duration.between(startInstant, endInstant)
            return startInstant.minus(len)
        }
    val previousEndInstant: Instant get() = startInstant
}
