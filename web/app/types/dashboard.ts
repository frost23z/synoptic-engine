/**
 * Dashboard contracts.
 *
 * Two backend surfaces, both gated on `reports.view`:
 *  - `GET /api/dashboard` → {@link DashboardSummaryResponse} (live snapshot;
 *    `DashboardController`). Present in the OpenAPI spec.
 *  - `GET /api/dashboard/stats?type=…` → the eight Krayin-parity stat groups
 *    below (`DashboardStatsController`). The endpoint returns `Any`, so these
 *    shapes are hand-mirrored from `crm/dashboard/web/DashboardStatsDtos.kt`
 *    (the spec only types it as a bare object).
 */

// ── Summary (/api/dashboard) ──────────────────────────────────────────────

export interface StageStatsResponse {
    stageId: string
    stageName: string
    count: number
    value: number
}

export interface SalespersonStatsResponse {
    userId: string
    userName: string
    leadsWon: number
    revenue: number
}

export interface ActivitySummaryResponse {
    id: string
    title: string
    type: string
    /** Backend DTO field is `isDone` (not `done`). */
    isDone: boolean
    scheduleFrom?: string | null
    scheduleTo?: string | null
}

export interface DashboardSummaryResponse {
    totalLeads: number
    openLeads: number
    wonLeads: number
    lostLeads: number
    totalRevenue: number
    leadsByStage: StageStatsResponse[]
    recentActivities: ActivitySummaryResponse[]
    upcomingActivities: ActivitySummaryResponse[]
    topSalespeople: SalespersonStatsResponse[]
}

// ── Stats (/api/dashboard/stats?type=…) ───────────────────────────────────

/** Count with period-over-period delta. */
export interface PeriodCount {
    current: number
    previous: number
    delta: number
    changePercent: number
}

/** Monetary/decimal value with period-over-period delta. */
export interface PeriodValue {
    current: number
    previous: number
    delta: number
    changePercent: number
}

/** `type=over-all` */
export interface OverAllStatsResponse {
    leads: PeriodCount
    averageLeadValue: PeriodValue
    averageLeadsPerDay: PeriodValue
    activities: PeriodCount
    quotes: PeriodCount
    persons: PeriodCount
    organizations: PeriodCount
}

/** `type=revenue-stats` */
export interface RevenueStatsResponse {
    wonRevenue: number
    lostRevenue: number
    previousWonRevenue: number
    previousLostRevenue: number
}

export interface TimeSeriesBucket {
    /** ISO date (yyyy-MM-dd) at the start of the bucket. */
    date: string
    count: number
}

export interface TotalLeadsSeries {
    overTime: TimeSeriesBucket[]
}

/** `type=total-leads` (bucket = day | week | month) */
export interface TotalLeadsResponse {
    bucket: string
    series: TimeSeriesBucket[]
    all: TotalLeadsSeries
    won: TotalLeadsSeries
    lost: TotalLeadsSeries
}

/** `type=revenue-by-sources` / `revenue-by-types` */
export interface RevenueByDimensionEntry {
    id: string
    name: string
    revenue: number
    count: number
}

/** `type=top-selling-products` */
export interface TopProductEntry {
    productId: string
    productName: string
    sku?: string | null
    totalQuantity: number
    totalRevenue: number
}

/** `type=top-persons` */
export interface TopPersonEntry {
    personId: string
    name: string
    wonLeads: number
    revenue: number
}

/** `type=open-leads-by-states` */
export interface OpenLeadsByStateEntry {
    stageId: string
    stageName: string
    stageColor?: string | null
    count: number
    value: number
}
