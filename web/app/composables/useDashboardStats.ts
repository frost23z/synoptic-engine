import type {
    DashboardSummaryResponse,
    OpenLeadsByStateEntry,
    OverAllStatsResponse,
    RevenueByDimensionEntry,
    RevenueStatsResponse,
    TopPersonEntry,
    TopProductEntry,
    TotalLeadsResponse,
} from '~/types/dashboard'

export interface DashboardDateRange {
    /** Inclusive ISO date (yyyy-MM-dd). */
    start: string
    /** Inclusive ISO date (yyyy-MM-dd). */
    end: string
}

/** All eight stat groups fetched for the current date range. */
export interface DashboardStatsBundle {
    overAll: OverAllStatsResponse
    revenue: RevenueStatsResponse
    totalLeads: TotalLeadsResponse
    revenueBySources: RevenueByDimensionEntry[]
    revenueByTypes: RevenueByDimensionEntry[]
    topProducts: TopProductEntry[]
    topPersons: TopPersonEntry[]
    openLeadsByStates: OpenLeadsByStateEntry[]
}

export interface DashboardPreset {
    key: string
    label: string
    range: () => DashboardDateRange
}

/** Local yyyy-MM-dd (avoids the UTC off-by-one of `toISOString`). */
function toIso(d: Date): string {
    const y = d.getFullYear()
    const m = `${d.getMonth() + 1}`.padStart(2, '0')
    const day = `${d.getDate()}`.padStart(2, '0')
    return `${y}-${m}-${day}`
}

function daysAgo(n: number): string {
    const d = new Date()
    d.setDate(d.getDate() - n)
    return toIso(d)
}

export const DASHBOARD_PRESETS: DashboardPreset[] = [
    {
        key: 'last-7',
        label: 'Last 7 days',
        range: () => ({ start: daysAgo(7), end: toIso(new Date()) }),
    },
    {
        key: 'last-30',
        label: 'Last 30 days',
        range: () => ({ start: daysAgo(30), end: toIso(new Date()) }),
    },
    {
        key: 'last-90',
        label: 'Last 90 days',
        range: () => ({ start: daysAgo(90), end: toIso(new Date()) }),
    },
    {
        key: 'this-month',
        label: 'This month',
        range: () => {
            const now = new Date()
            return { start: toIso(new Date(now.getFullYear(), now.getMonth(), 1)), end: toIso(now) }
        },
    },
    {
        key: 'this-year',
        label: 'This year',
        range: () => {
            const now = new Date()
            return { start: toIso(new Date(now.getFullYear(), 0, 1)), end: toIso(now) }
        },
    },
]

export const DEFAULT_PRESET = 'last-30'

/** Auto bucket selection, mirroring Krayin (backend supports day/week/month). */
function pickBucket(start: string, end: string): 'day' | 'week' | 'month' {
    const days = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 86_400_000)
    if (days > 180) return 'month'
    if (days > 60) return 'week'
    return 'day'
}

/**
 * Loads the dashboard: the eight `/dashboard/stats` groups (date-range driven,
 * fetched in parallel) plus the `/dashboard` summary snapshot. All calls are
 * gated on `reports.view`; without it nothing is fetched and the page renders a
 * permission-empty state.
 */
export function useDashboardStats() {
    const api = useApi()
    const { can } = usePermissions()
    const canView = can('reports.view')

    const range = reactive<DashboardDateRange>(
        DASHBOARD_PRESETS.find((p) => p.key === DEFAULT_PRESET)!.range()
    )
    const bucket = computed(() => pickBucket(range.start, range.end))

    const stat = <T>(type: string, extra?: Record<string, string>) =>
        api<T>('/api/dashboard/stats', {
            params: { type, startDate: range.start, endDate: range.end, ...extra },
        })

    const {
        data: stats,
        pending,
        error,
        refresh,
    } = useAsyncData<DashboardStatsBundle | null>(
        'dashboard-stats',
        async () => {
            const [
                overAll,
                revenue,
                totalLeads,
                revenueBySources,
                revenueByTypes,
                topProducts,
                topPersons,
                openLeadsByStates,
            ] = await Promise.all([
                stat<OverAllStatsResponse>('over-all'),
                stat<RevenueStatsResponse>('revenue-stats'),
                stat<TotalLeadsResponse>('total-leads', { bucket: bucket.value }),
                stat<RevenueByDimensionEntry[]>('revenue-by-sources'),
                stat<RevenueByDimensionEntry[]>('revenue-by-types'),
                stat<TopProductEntry[]>('top-selling-products'),
                stat<TopPersonEntry[]>('top-persons'),
                stat<OpenLeadsByStateEntry[]>('open-leads-by-states'),
            ])
            return {
                overAll,
                revenue,
                totalLeads,
                revenueBySources,
                revenueByTypes,
                topProducts,
                topPersons,
                openLeadsByStates,
            }
        },
        { watch: [() => range.start, () => range.end], immediate: canView }
    )

    const { data: summary, pending: summaryPending } =
        useAsyncData<DashboardSummaryResponse | null>(
            'dashboard-summary',
            () => api<DashboardSummaryResponse>('/api/dashboard'),
            { immediate: canView }
        )

    function applyPreset(key: string) {
        const preset = DASHBOARD_PRESETS.find((p) => p.key === key)
        if (preset) Object.assign(range, preset.range())
    }

    return {
        canView,
        range,
        bucket,
        stats,
        pending,
        error,
        refresh,
        summary,
        summaryPending,
        presets: DASHBOARD_PRESETS,
        applyPreset,
    }
}
