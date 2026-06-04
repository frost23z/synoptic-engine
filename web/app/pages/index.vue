<script setup lang="ts">
import type { BarListItem } from '~/components/AppBarList.vue'
import type { LineChartSeries } from '~/components/AppLineChart.vue'
import { ACTIVITY_TYPE_ICON } from '~/types/activities'
import type { TimeSeriesBucket } from '~/types/dashboard'

definePageMeta({ title: 'Dashboard' })
useHead({ title: 'Dashboard — Synoptic' })

const authStore = useAuthStore()
const { formatCurrency, formatRelativeDate } = useFormatters()

const {
    canView,
    range,
    bucket,
    stats,
    pending,
    error,
    summary,
    summaryPending,
    presets,
    applyPreset,
} = useDashboardStats()

// ── Date range control ────────────────────────────────────────────────────
const presetKey = ref('last-30')
const presetItems = computed(() => [
    ...presets.map((p) => ({ label: p.label, value: p.key })),
    { label: 'Custom', value: 'custom' },
])
watch(presetKey, (key) => {
    if (key !== 'custom') applyPreset(key)
})
function onDateEdit() {
    presetKey.value = 'custom'
}

const firstName = computed(() => authStore.user?.fullName?.split(' ')[0] ?? 'there')
const bucketLabel = computed(
    () => ({ day: 'daily', week: 'weekly', month: 'monthly' })[bucket.value]
)

// ── KPI tiles (over-all + revenue-stats) ───────────────────────────────────
const round1 = (n: number) => Math.round(n * 10) / 10
function pct(current: number, previous: number): number {
    if (previous === 0) return current === 0 ? 0 : 100
    return round1(((current - previous) / previous) * 100)
}

const kpis = computed(() => {
    const o = stats.value?.overAll
    const r = stats.value?.revenue
    return [
        {
            label: 'Total Leads',
            icon: 'i-tabler-chart-bar',
            iconColor: 'text-primary',
            iconBg: 'bg-primary/10',
            value: o?.leads.current ?? '—',
            trend: o?.leads.changePercent,
        },
        {
            label: 'Won Revenue',
            icon: 'i-tabler-trophy',
            iconColor: 'text-success',
            iconBg: 'bg-success/10',
            value: r ? formatCurrency(r.wonRevenue) : '—',
            trend: r ? pct(r.wonRevenue, r.previousWonRevenue) : undefined,
        },
        {
            label: 'Lost Revenue',
            icon: 'i-tabler-trending-down',
            iconColor: 'text-error',
            iconBg: 'bg-error/10',
            value: r ? formatCurrency(r.lostRevenue) : '—',
            trend: r ? pct(r.lostRevenue, r.previousLostRevenue) : undefined,
            invert: true,
        },
        {
            label: 'Avg Lead Value',
            icon: 'i-tabler-currency-dollar',
            iconColor: 'text-warning',
            iconBg: 'bg-warning/10',
            value: o ? formatCurrency(o.averageLeadValue.current) : '—',
            trend: o?.averageLeadValue.changePercent,
        },
        {
            label: 'Avg Leads / Day',
            icon: 'i-tabler-calendar-stats',
            iconColor: 'text-info',
            iconBg: 'bg-info/10',
            value: o ? round1(o.averageLeadsPerDay.current) : '—',
            trend: o?.averageLeadsPerDay.changePercent,
        },
        {
            label: 'Activities',
            icon: 'i-tabler-activity',
            iconColor: 'text-primary',
            iconBg: 'bg-primary/10',
            value: o?.activities.current ?? '—',
            trend: o?.activities.changePercent,
        },
        {
            label: 'Quotations',
            icon: 'i-tabler-file-invoice',
            iconColor: 'text-info',
            iconBg: 'bg-info/10',
            value: o?.quotes.current ?? '—',
            trend: o?.quotes.changePercent,
        },
        {
            label: 'Persons',
            icon: 'i-tabler-user',
            iconColor: 'text-success',
            iconBg: 'bg-success/10',
            value: o?.persons.current ?? '—',
            trend: o?.persons.changePercent,
        },
        {
            label: 'Organizations',
            icon: 'i-tabler-building',
            iconColor: 'text-warning',
            iconBg: 'bg-warning/10',
            value: o?.organizations.current ?? '—',
            trend: o?.organizations.changePercent,
        },
    ]
})

// ── Bar-list widgets ────────────────────────────────────────────────────────
const openLeadsBars = computed<BarListItem[]>(() =>
    (stats.value?.openLeadsByStates ?? []).map((s) => ({
        id: s.stageId,
        label: s.stageName,
        value: s.count,
        display: s.count,
        secondary: formatCurrency(s.value),
        color: s.stageColor ?? null,
    }))
)
const sourceBars = computed<BarListItem[]>(() =>
    (stats.value?.revenueBySources ?? []).map((s) => ({
        id: s.id,
        label: s.name,
        value: s.revenue,
        display: formatCurrency(s.revenue),
    }))
)
const typeBars = computed<BarListItem[]>(() =>
    (stats.value?.revenueByTypes ?? []).map((s) => ({
        id: s.id,
        label: s.name,
        value: s.revenue,
        display: formatCurrency(s.revenue),
    }))
)

// ── Leads-over-time chart (zero-filled union of all/won/lost buckets) ───────
const leadsChart = computed<{ labels: string[]; series: LineChartSeries[] }>(() => {
    const tl = stats.value?.totalLeads
    if (!tl) return { labels: [], series: [] }
    const dates = [
        ...new Set(
            [...tl.all.overTime, ...tl.won.overTime, ...tl.lost.overTime].map((b) => b.date)
        ),
    ].sort()
    if (!dates.length) return { labels: [], series: [] }
    const toMap = (arr: TimeSeriesBucket[]) => new Map(arr.map((b) => [b.date, b.count]))
    const all = toMap(tl.all.overTime)
    const won = toMap(tl.won.overTime)
    const lost = toMap(tl.lost.overTime)
    const fmt = (d: string) =>
        new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    return {
        labels: dates.map(fmt),
        series: [
            { label: 'All', color: 'text-primary', data: dates.map((d) => all.get(d) ?? 0) },
            { label: 'Won', color: 'text-success', data: dates.map((d) => won.get(d) ?? 0) },
            { label: 'Lost', color: 'text-error', data: dates.map((d) => lost.get(d) ?? 0) },
        ],
    }
})

function activityIcon(type: string) {
    return ACTIVITY_TYPE_ICON[type as keyof typeof ACTIVITY_TYPE_ICON] ?? 'i-tabler-activity'
}
</script>

<template>
    <div class="space-y-6">
        <AppPageHeader
            title="Dashboard"
            :subtitle="`Welcome back, ${firstName} — your workspace at a glance.`"
        >
            <template v-if="canView" #actions>
                <USelect v-model="presetKey" :items="presetItems" size="sm" class="w-36" />
                <UInput v-model="range.start" type="date" size="sm" @change="onDateEdit" />
                <span class="text-muted text-sm">–</span>
                <UInput v-model="range.end" type="date" size="sm" @change="onDateEdit" />
            </template>
        </AppPageHeader>

        <AppEmptyState
            v-if="!canView"
            icon="i-tabler-lock"
            message="You don't have permission to view dashboard reports."
        />

        <template v-else>
            <UAlert
                v-if="error"
                color="error"
                variant="soft"
                icon="i-tabler-alert-triangle"
                title="Couldn't load some dashboard data"
                description="Try adjusting the date range, or reload the page."
            />

            <!-- KPI tiles -->
            <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
                <AppStatCard
                    v-for="k in kpis"
                    :key="k.label"
                    :label="k.label"
                    :icon="k.icon"
                    :icon-color="k.iconColor"
                    :icon-bg="k.iconBg"
                    :value="k.value"
                    :trend="k.trend"
                    :invert-trend="k.invert"
                    :loading="pending"
                />
            </div>

            <!-- Leads over time -->
            <UCard>
                <template #header>
                    <div class="flex items-center justify-between">
                        <p class="text-highlighted font-semibold">Leads Over Time</p>
                        <span class="text-muted text-xs capitalize">{{ bucketLabel }}</span>
                    </div>
                </template>
                <AppLineChart
                    :series="leadsChart.series"
                    :labels="leadsChart.labels"
                    :loading="pending"
                />
            </UCard>

            <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
                <!-- Open leads by stage -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Open Leads by Stage</p>
                    </template>
                    <AppBarList
                        :items="openLeadsBars"
                        :loading="pending"
                        empty-message="No open leads"
                    />
                </UCard>

                <!-- Revenue by source -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Revenue by Source</p>
                    </template>
                    <AppBarList
                        :items="sourceBars"
                        :loading="pending"
                        empty-message="No revenue yet"
                    />
                </UCard>

                <!-- Revenue by type -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Revenue by Type</p>
                    </template>
                    <AppBarList
                        :items="typeBars"
                        :loading="pending"
                        empty-message="No revenue yet"
                    />
                </UCard>

                <!-- Top selling products -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Top Selling Products</p>
                    </template>
                    <div v-if="pending" class="space-y-3">
                        <div v-for="i in 5" :key="i" class="flex items-center justify-between">
                            <USkeleton class="h-4 w-40" />
                            <USkeleton class="h-4 w-20" />
                        </div>
                    </div>
                    <div
                        v-else-if="!stats?.topProducts.length"
                        class="text-muted py-6 text-center text-sm"
                    >
                        No products sold yet
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="(p, idx) in stats.topProducts"
                            :key="p.productId"
                            class="flex items-center justify-between py-2.5"
                        >
                            <div class="flex min-w-0 items-center gap-2.5">
                                <span class="text-muted w-4 shrink-0 text-xs">{{ idx + 1 }}</span>
                                <div class="min-w-0">
                                    <p class="text-default truncate text-sm font-medium">
                                        {{ p.productName }}
                                    </p>
                                    <p v-if="p.sku" class="text-dimmed truncate text-xs">
                                        {{ p.sku }}
                                    </p>
                                </div>
                            </div>
                            <div class="shrink-0 pl-2 text-right">
                                <p class="text-highlighted text-sm font-semibold">
                                    {{ formatCurrency(p.totalRevenue) }}
                                </p>
                                <p class="text-muted text-xs">{{ p.totalQuantity }} sold</p>
                            </div>
                        </li>
                    </ul>
                </UCard>

                <!-- Top persons -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Top Customers</p>
                    </template>
                    <div v-if="pending" class="space-y-3">
                        <div v-for="i in 5" :key="i" class="flex items-center justify-between">
                            <USkeleton class="h-4 w-32" />
                            <USkeleton class="h-4 w-20" />
                        </div>
                    </div>
                    <div
                        v-else-if="!stats?.topPersons.length"
                        class="text-muted py-6 text-center text-sm"
                    >
                        No customers yet
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="(person, idx) in stats.topPersons"
                            :key="person.personId"
                            class="flex items-center justify-between py-2.5"
                        >
                            <div class="flex min-w-0 items-center gap-2.5">
                                <span class="text-muted w-4 shrink-0 text-xs">{{ idx + 1 }}</span>
                                <UAvatar :alt="person.name" size="xs" />
                                <span class="text-default truncate text-sm font-medium">
                                    {{ person.name }}
                                </span>
                            </div>
                            <div class="shrink-0 pl-2 text-right">
                                <p class="text-highlighted text-sm font-semibold">
                                    {{ formatCurrency(person.revenue) }}
                                </p>
                                <p class="text-muted text-xs">{{ person.wonLeads }} won</p>
                            </div>
                        </li>
                    </ul>
                </UCard>

                <!-- Top salespeople (summary snapshot) -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Top Salespeople</p>
                    </template>
                    <div v-if="summaryPending" class="space-y-3">
                        <div v-for="i in 5" :key="i" class="flex items-center justify-between">
                            <USkeleton class="h-4 w-32" />
                            <USkeleton class="h-4 w-20" />
                        </div>
                    </div>
                    <div
                        v-else-if="!summary?.topSalespeople.length"
                        class="text-muted py-6 text-center text-sm"
                    >
                        No data yet
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="(sp, idx) in summary.topSalespeople"
                            :key="sp.userId"
                            class="flex items-center justify-between py-2.5"
                        >
                            <div class="flex min-w-0 items-center gap-2.5">
                                <span class="text-muted w-4 shrink-0 text-xs">{{ idx + 1 }}</span>
                                <UAvatar :alt="sp.userName" size="xs" />
                                <span class="text-default truncate text-sm font-medium">
                                    {{ sp.userName }}
                                </span>
                            </div>
                            <div class="shrink-0 pl-2 text-right">
                                <p class="text-highlighted text-sm font-semibold">
                                    {{ formatCurrency(sp.revenue) }}
                                </p>
                                <p class="text-muted text-xs">{{ sp.leadsWon }} won</p>
                            </div>
                        </li>
                    </ul>
                </UCard>
            </div>

            <!-- Recent activity (live snapshot) -->
            <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">Recent Activities</p>
                            <UButton
                                label="View all"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                to="/activities"
                            />
                        </div>
                    </template>
                    <div v-if="summaryPending" class="space-y-3">
                        <div v-for="i in 4" :key="i" class="flex items-center gap-3">
                            <USkeleton class="size-7 rounded-full" />
                            <div class="flex-1 space-y-1.5">
                                <USkeleton class="h-3.5 w-48" />
                                <USkeleton class="h-3 w-24" />
                            </div>
                        </div>
                    </div>
                    <div
                        v-else-if="!summary?.recentActivities.length"
                        class="text-muted py-6 text-center text-sm"
                    >
                        No recent activities
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="act in summary.recentActivities"
                            :key="act.id"
                            class="flex items-start gap-3 py-2.5"
                        >
                            <div class="bg-muted mt-0.5 shrink-0 rounded-full p-1.5">
                                <UIcon :name="activityIcon(act.type)" class="text-muted size-3.5" />
                            </div>
                            <div class="min-w-0 flex-1">
                                <p
                                    class="text-default truncate text-sm"
                                    :class="act.isDone ? 'line-through opacity-60' : ''"
                                >
                                    {{ act.title }}
                                </p>
                                <p class="text-muted text-xs">
                                    {{ formatRelativeDate(act.scheduleFrom) }}
                                </p>
                            </div>
                            <UBadge
                                v-if="act.isDone"
                                label="Done"
                                color="success"
                                variant="soft"
                                size="xs"
                            />
                        </li>
                    </ul>
                </UCard>

                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">Upcoming Activities</p>
                            <UButton
                                label="View all"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                to="/activities"
                            />
                        </div>
                    </template>
                    <div v-if="summaryPending" class="space-y-3">
                        <div v-for="i in 4" :key="i" class="flex items-center gap-3">
                            <USkeleton class="size-7 rounded-full" />
                            <div class="flex-1 space-y-1.5">
                                <USkeleton class="h-3.5 w-48" />
                                <USkeleton class="h-3 w-24" />
                            </div>
                        </div>
                    </div>
                    <div
                        v-else-if="!summary?.upcomingActivities.length"
                        class="text-muted py-6 text-center text-sm"
                    >
                        Nothing scheduled
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="act in summary.upcomingActivities"
                            :key="act.id"
                            class="flex items-start gap-3 py-2.5"
                        >
                            <div class="bg-primary/10 mt-0.5 shrink-0 rounded-full p-1.5">
                                <UIcon
                                    :name="activityIcon(act.type)"
                                    class="text-primary size-3.5"
                                />
                            </div>
                            <div class="min-w-0 flex-1">
                                <p class="text-default truncate text-sm">{{ act.title }}</p>
                                <p class="text-muted text-xs">
                                    {{ formatRelativeDate(act.scheduleFrom) }}
                                </p>
                            </div>
                            <UBadge :label="act.type" color="neutral" variant="soft" size="xs" />
                        </li>
                    </ul>
                </UCard>
            </div>
        </template>
    </div>
</template>
