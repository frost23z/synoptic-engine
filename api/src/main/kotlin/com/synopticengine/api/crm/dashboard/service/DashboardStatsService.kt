package com.synopticengine.api.crm.dashboard.service

import com.synopticengine.api.crm.activity.repo.ActivityRepository
import com.synopticengine.api.crm.contact.repo.PersonRepository
import com.synopticengine.api.crm.dashboard.DateRange
import com.synopticengine.api.crm.dashboard.web.OpenLeadsByStateEntry
import com.synopticengine.api.crm.dashboard.web.OverAllStatsResponse
import com.synopticengine.api.crm.dashboard.web.PeriodCount
import com.synopticengine.api.crm.dashboard.web.RevenueByDimensionEntry
import com.synopticengine.api.crm.dashboard.web.RevenueStatsResponse
import com.synopticengine.api.crm.dashboard.web.TimeSeriesBucket
import com.synopticengine.api.crm.dashboard.web.TopPersonEntry
import com.synopticengine.api.crm.dashboard.web.TopProductEntry
import com.synopticengine.api.crm.dashboard.web.TotalLeadsResponse
import com.synopticengine.api.crm.lead.repo.LeadRepository
import com.synopticengine.api.crm.lead.repo.LeadSourceRepository
import com.synopticengine.api.crm.lead.repo.LeadTypeRepository
import com.synopticengine.api.crm.lead.repo.StageRepository
import com.synopticengine.api.crm.quote.repo.QuoteRepository
import com.synopticengine.api.crm.scoping.ScopeResolver
import com.synopticengine.api.shared.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DashboardStatsService(
    private val leadRepository: LeadRepository,
    private val activityRepository: ActivityRepository,
    private val quoteRepository: QuoteRepository,
    private val personRepository: PersonRepository,
    private val stageRepository: StageRepository,
    private val leadSourceRepository: LeadSourceRepository,
    private val leadTypeRepository: LeadTypeRepository,
    private val scopeResolver: ScopeResolver,
) {
    /**
     * `over-all`: counts of leads / activities / quotes / persons in the current period
     * vs the immediately preceding period of the same length, with delta + % change.
     */
    fun overAll(range: DateRange): OverAllStatsResponse {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) {
            // User has no view scope → everything is zero.
            return OverAllStatsResponse(zero, zero, zero, zero)
        }
        val tenantId = requireTenant()
        val sb = scopeBinding(scope)
        val leadsCurrent =
            leadRepository.countCreatedInRangeNative(range.startInstant, range.endInstant, sb.hasScope, sb.ids).toInt()
        val leadsPrevious =
            leadRepository
                .countCreatedInRangeNative(range.previousStartInstant, range.previousEndInstant, sb.hasScope, sb.ids)
                .toInt()
        val activitiesCurrent =
            activityRepository
                .countCreatedInRangeNative(tenantId, range.startInstant, range.endInstant, sb.hasScope, sb.ids)
                .toInt()
        val activitiesPrevious =
            activityRepository
                .countCreatedInRangeNative(
                    tenantId,
                    range.previousStartInstant,
                    range.previousEndInstant,
                    sb.hasScope,
                    sb.ids,
                ).toInt()
        val quotesCurrent =
            quoteRepository
                .countCreatedInRangeNative(
                    tenantId,
                    range.startInstant,
                    range.endInstant,
                    sb.hasScope,
                    sb.ids,
                ).toInt()
        val quotesPrevious =
            quoteRepository
                .countCreatedInRangeNative(
                    tenantId,
                    range.previousStartInstant,
                    range.previousEndInstant,
                    sb.hasScope,
                    sb.ids,
                ).toInt()
        // Persons aren't user-scoped today (CRM-wide list); count as-is.
        val personsCurrent =
            personRepository.countCreatedInRangeNative(range.startInstant, range.endInstant).toInt()
        val personsPrevious =
            personRepository.countCreatedInRangeNative(range.previousStartInstant, range.previousEndInstant).toInt()
        return OverAllStatsResponse(
            leads = period(leadsCurrent, leadsPrevious),
            activities = period(activitiesCurrent, activitiesPrevious),
            quotes = period(quotesCurrent, quotesPrevious),
            persons = period(personsCurrent, personsPrevious),
        )
    }

    /** `revenue-stats`: sum of won amount and lost amount in the period. */
    fun revenueStats(range: DateRange): RevenueStatsResponse {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) {
            return RevenueStatsResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        }
        val sb = scopeBinding(scope)
        val won =
            leadRepository.sumAmountByStatusInRangeNative(
                "won",
                range.startInstant,
                range.endInstant,
                sb.hasScope,
                sb.ids,
            )
        val lost =
            leadRepository.sumAmountByStatusInRangeNative(
                "lost",
                range.startInstant,
                range.endInstant,
                sb.hasScope,
                sb.ids,
            )
        val wonPrev =
            leadRepository.sumAmountByStatusInRangeNative(
                "won",
                range.previousStartInstant,
                range.previousEndInstant,
                sb.hasScope,
                sb.ids,
            )
        val lostPrev =
            leadRepository.sumAmountByStatusInRangeNative(
                "lost",
                range.previousStartInstant,
                range.previousEndInstant,
                sb.hasScope,
                sb.ids,
            )
        return RevenueStatsResponse(
            wonRevenue = won,
            lostRevenue = lost,
            previousWonRevenue = wonPrev,
            previousLostRevenue = lostPrev,
        )
    }

    /**
     * `total-leads`: time-series of lead create count bucketed by `day` (default),
     * `week`, or `month`.
     */
    fun totalLeads(
        range: DateRange,
        bucket: String,
    ): TotalLeadsResponse {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) {
            return TotalLeadsResponse(bucket = bucket, series = emptyList())
        }
        val sb = scopeBinding(scope)
        val pgBucket =
            when (bucket.lowercase()) {
                "month" -> "month"
                "week" -> "week"
                else -> "day"
            }
        val rows =
            leadRepository.countCreatedByBucketNative(
                pgBucket,
                range.startInstant,
                range.endInstant,
                sb.hasScope,
                sb.ids,
            )
        return TotalLeadsResponse(
            bucket = pgBucket,
            series =
                rows.map { row ->
                    TimeSeriesBucket(
                        date = toLocalDate(row[0]),
                        count = (row[1] as Number).toInt(),
                    )
                },
        )
    }

    /** PostgreSQL's `::date` cast hits the JDBC driver as either `java.sql.Date` or `java.time.LocalDate`. */
    private fun toLocalDate(raw: Any): java.time.LocalDate =
        when (raw) {
            is java.time.LocalDate -> raw
            is java.sql.Date -> raw.toLocalDate()
            else -> java.time.LocalDate.parse(raw.toString())
        }

    /** `revenue-by-sources`: sum of won amount grouped by lead_source_id. */
    fun revenueBySources(range: DateRange): List<RevenueByDimensionEntry> {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) return emptyList()
        val sb = scopeBinding(scope)
        val rows =
            leadRepository.revenueByLeadSourceInRangeNative(
                range.startInstant,
                range.endInstant,
                sb.hasScope,
                sb.ids,
            )
        if (rows.isEmpty()) return emptyList()
        val sourceNames = leadSourceRepository.findAll().associateBy({ it.id!! }, { it.name })
        return rows.map { row ->
            val sourceId = row[0] as UUID
            RevenueByDimensionEntry(
                id = sourceId,
                name = sourceNames[sourceId] ?: "Unknown",
                revenue = (row[1] as? BigDecimal) ?: BigDecimal.ZERO,
                count = (row[2] as Number).toInt(),
            )
        }
    }

    /** `revenue-by-types`: sum of won amount grouped by lead_type_id. */
    fun revenueByTypes(range: DateRange): List<RevenueByDimensionEntry> {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) return emptyList()
        val sb = scopeBinding(scope)
        val rows =
            leadRepository.revenueByLeadTypeInRangeNative(
                range.startInstant,
                range.endInstant,
                sb.hasScope,
                sb.ids,
            )
        if (rows.isEmpty()) return emptyList()
        val typeNames = leadTypeRepository.findAll().associateBy({ it.id!! }, { it.name })
        return rows.map { row ->
            val typeId = row[0] as UUID
            RevenueByDimensionEntry(
                id = typeId,
                name = typeNames[typeId] ?: "Unknown",
                revenue = (row[1] as? BigDecimal) ?: BigDecimal.ZERO,
                count = (row[2] as Number).toInt(),
            )
        }
    }

    /** `top-selling-products`: sum of lead_products.quantity * unit_price grouped by product. */
    fun topSellingProducts(
        range: DateRange,
        limit: Int,
    ): List<TopProductEntry> {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) return emptyList()
        val sb = scopeBinding(scope)
        val rows =
            leadRepository.topSellingProductsInRangeNative(
                range.startInstant,
                range.endInstant,
                sb.hasScope,
                sb.ids,
                limit,
            )
        if (rows.isEmpty()) return emptyList()
        // Product name/sku are fetched in the same SQL (LEFT JOIN products) —
        // avoids a separate cross-module call to `InventoryApi` which would
        // introduce a CRM → inventory dependency cycle.
        return rows.map { row ->
            TopProductEntry(
                productId = row[0] as UUID,
                productName = (row[3] as? String) ?: "Unknown",
                sku = row[4] as? String,
                totalQuantity = (row[1] as Number).toInt(),
                totalRevenue = (row[2] as? BigDecimal) ?: BigDecimal.ZERO,
            )
        }
    }

    /** `top-persons`: top customers by won-lead amount, top N. */
    fun topPersons(
        range: DateRange,
        limit: Int,
    ): List<TopPersonEntry> {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) return emptyList()
        val sb = scopeBinding(scope)
        val rows =
            leadRepository.topPersonsByRevenueInRangeNative(
                range.startInstant,
                range.endInstant,
                sb.hasScope,
                sb.ids,
                limit,
            )
        if (rows.isEmpty()) return emptyList()
        val personIds = rows.map { it[0] as UUID }
        val persons = personRepository.findAllById(personIds).associateBy { it.id!! }
        return rows.map { row ->
            val personId = row[0] as UUID
            val person = persons[personId]
            TopPersonEntry(
                personId = personId,
                name = person?.fullName ?: "Unknown",
                wonLeads = (row[1] as Number).toInt(),
                revenue = (row[2] as? BigDecimal) ?: BigDecimal.ZERO,
            )
        }
    }

    /** `open-leads-by-states`: count of OPEN leads per stage. */
    fun openLeadsByStates(): List<OpenLeadsByStateEntry> {
        val scope = scopeResolver.userIdsForCurrentUser()
        if (scope?.isEmpty() == true) return emptyList()
        val sb = scopeBinding(scope)
        val rows = leadRepository.openLeadsByStageNative(sb.hasScope, sb.ids)
        if (rows.isEmpty()) return emptyList()
        val stageIds = rows.map { it[0] as UUID }
        val stages = stageRepository.findAllByIdIn(stageIds).associateBy { it.id!! }
        return rows.map { row ->
            val stageId = row[0] as UUID
            val stage = stages[stageId]
            OpenLeadsByStateEntry(
                stageId = stageId,
                stageName = stage?.name ?: "Unknown",
                stageColor = stage?.color,
                count = (row[1] as Number).toInt(),
                value = (row[2] as? BigDecimal) ?: BigDecimal.ZERO,
            )
        }
    }

    private fun period(
        current: Int,
        previous: Int,
    ): PeriodCount {
        val delta = current - previous
        val pct =
            if (previous == 0) {
                if (current == 0) BigDecimal.ZERO else BigDecimal(100)
            } else {
                BigDecimal(delta)
                    .multiply(BigDecimal(100))
                    .divide(BigDecimal(previous), 2, java.math.RoundingMode.HALF_UP)
            }
        return PeriodCount(current = current, previous = previous, delta = delta, changePercent = pct)
    }

    /**
     * Native `IN (:list)` won't accept an empty collection, so when scope is unrestricted
     * we pass [PLACEHOLDER_IDS] and [hasScope] = false; the SQL skips the IN check.
     */
    private fun scopeBinding(scope: Set<UUID>?): ScopeBinding =
        if (scope == null) {
            ScopeBinding(hasScope = false, ids = PLACEHOLDER_IDS)
        } else {
            ScopeBinding(hasScope = true, ids = scope)
        }

    // Native dashboard queries against `activities` and `quotes` need an explicit
    // tenant predicate because those tables are not RLS-protected (V007 only
    // enables RLS on leads/orgs/persons/products).
    private fun requireTenant(): UUID =
        TenantContext.get() ?: error("TenantContext not set; dashboard endpoints require authentication")

    private data class ScopeBinding(
        val hasScope: Boolean,
        val ids: Collection<UUID>,
    )

    companion object {
        private val PLACEHOLDER_IDS: Collection<UUID> = listOf(UUID(0L, 0L))
        private val zero = PeriodCount(0, 0, 0, BigDecimal.ZERO)
    }
}
