<script setup lang="ts">
import { ACTIVITY_TYPE_ICON } from '~/types/activities'

definePageMeta({ title: 'Dashboard' })
useHead({ title: 'Dashboard — Synoptic' })

const authStore = useAuthStore()
const { formatCurrency, formatRelativeDate } = useFormatters()

interface StageStatsResponse {
    stageId: string
    stageName: string
    count: number
    value: number
}

interface ActivitySummaryResponse {
    id: string
    title: string
    type: string
    scheduleFrom: string
    scheduleTo: string
    done: boolean
}

interface SalespersonStatsResponse {
    userId: string
    userName: string
    leadsWon: number
    revenue: number
}

interface DashboardResponse {
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

const api = useApi()

const { data: dashboard, pending } = await useAsyncData<DashboardResponse>('dashboard', () =>
    api<DashboardResponse>('/api/dashboard')
)

const statCards = computed(() => [
    {
        label: 'Total Leads',
        icon: 'i-tabler-chart-bar',
        value: dashboard.value?.totalLeads ?? '—',
        color: 'text-primary',
        bg: 'bg-primary/10',
    },
    {
        label: 'Open Leads',
        icon: 'i-tabler-circle-dot',
        value: dashboard.value?.openLeads ?? '—',
        color: 'text-info',
        bg: 'bg-info/10',
    },
    {
        label: 'Won Leads',
        icon: 'i-tabler-trophy',
        value: dashboard.value?.wonLeads ?? '—',
        color: 'text-success',
        bg: 'bg-success/10',
    },
    {
        label: 'Total Revenue',
        icon: 'i-tabler-currency-dollar',
        value: dashboard.value ? formatCurrency(dashboard.value.totalRevenue) : '—',
        color: 'text-warning',
        bg: 'bg-warning/10',
    },
])

const maxStageCount = computed(() =>
    Math.max(1, ...(dashboard.value?.leadsByStage.map((s) => s.count) ?? [1]))
)

function activityIcon(type: string) {
    return ACTIVITY_TYPE_ICON[type as keyof typeof ACTIVITY_TYPE_ICON] ?? 'i-tabler-activity'
}
</script>

<template>
    <div class="space-y-6">
        <!-- Welcome -->
        <div>
            <h2 class="text-highlighted text-xl font-semibold">
                Welcome back, {{ authStore.user?.fullName?.split(' ')[0] ?? 'there' }}
            </h2>
            <p class="text-muted mt-1 text-sm">Here's what's happening in your workspace.</p>
        </div>

        <!-- Stat cards -->
        <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <AppStatCard
                v-for="stat in statCards"
                :key="stat.label"
                :label="stat.label"
                :icon="stat.icon"
                :value="stat.value"
                :icon-color="stat.color"
                :icon-bg="stat.bg"
                :loading="pending"
            />
        </div>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <!-- Leads by stage -->
            <UCard>
                <template #header>
                    <p class="text-highlighted font-semibold">Leads by Stage</p>
                </template>
                <div v-if="pending" class="space-y-3">
                    <div v-for="i in 4" :key="i" class="flex items-center gap-3">
                        <USkeleton class="h-3.5 w-24" />
                        <USkeleton class="h-4 flex-1" />
                        <USkeleton class="h-3.5 w-8" />
                    </div>
                </div>
                <div
                    v-else-if="!dashboard?.leadsByStage?.length"
                    class="text-muted py-6 text-center text-sm"
                >
                    No stage data yet
                </div>
                <div v-else class="space-y-3">
                    <div
                        v-for="stage in dashboard.leadsByStage"
                        :key="stage.stageId"
                        class="flex items-center gap-3"
                    >
                        <span class="text-muted w-28 shrink-0 truncate text-xs">{{
                            stage.stageName
                        }}</span>
                        <div
                            class="bg-muted flex-1 overflow-hidden rounded-full"
                            style="height: 6px"
                        >
                            <div
                                class="bg-primary h-full rounded-full transition-all"
                                :style="{ width: `${(stage.count / maxStageCount) * 100}%` }"
                            />
                        </div>
                        <span
                            class="text-highlighted w-6 shrink-0 text-right text-sm font-semibold"
                            >{{ stage.count }}</span
                        >
                    </div>
                </div>
            </UCard>

            <!-- Top salespeople -->
            <UCard>
                <template #header>
                    <p class="text-highlighted font-semibold">Top Salespeople</p>
                </template>
                <div v-if="pending" class="space-y-3">
                    <div v-for="i in 4" :key="i" class="flex items-center justify-between">
                        <USkeleton class="h-4 w-32" />
                        <USkeleton class="h-4 w-20" />
                    </div>
                </div>
                <div
                    v-else-if="!dashboard?.topSalespeople?.length"
                    class="text-muted py-6 text-center text-sm"
                >
                    No data yet
                </div>
                <ul v-else class="divide-default divide-y">
                    <li
                        v-for="(sp, idx) in dashboard.topSalespeople"
                        :key="sp.userId"
                        class="flex items-center justify-between py-2.5"
                    >
                        <div class="flex items-center gap-2.5">
                            <span class="text-muted w-4 text-xs">{{ idx + 1 }}</span>
                            <UAvatar :alt="sp.userName" size="xs" />
                            <span class="text-default text-sm font-medium">{{ sp.userName }}</span>
                        </div>
                        <div class="text-right">
                            <p class="text-highlighted text-sm font-semibold">
                                {{ formatCurrency(sp.revenue) }}
                            </p>
                            <p class="text-muted text-xs">{{ sp.leadsWon }} won</p>
                        </div>
                    </li>
                </ul>
            </UCard>

            <!-- Recent activities -->
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
                <div v-if="pending" class="space-y-3">
                    <div v-for="i in 4" :key="i" class="flex items-center gap-3">
                        <USkeleton class="size-7 rounded-full" />
                        <div class="flex-1 space-y-1.5">
                            <USkeleton class="h-3.5 w-48" />
                            <USkeleton class="h-3 w-24" />
                        </div>
                    </div>
                </div>
                <div
                    v-else-if="!dashboard?.recentActivities?.length"
                    class="text-muted py-6 text-center text-sm"
                >
                    No recent activities
                </div>
                <ul v-else class="divide-default divide-y">
                    <li
                        v-for="act in dashboard.recentActivities"
                        :key="act.id"
                        class="flex items-start gap-3 py-2.5"
                    >
                        <div class="bg-muted mt-0.5 shrink-0 rounded-full p-1.5">
                            <UIcon :name="activityIcon(act.type)" class="text-muted size-3.5" />
                        </div>
                        <div class="min-w-0 flex-1">
                            <p
                                class="text-default truncate text-sm"
                                :class="act.done ? 'line-through opacity-60' : ''"
                            >
                                {{ act.title }}
                            </p>
                            <p class="text-muted text-xs">
                                {{ formatRelativeDate(act.scheduleFrom) }}
                            </p>
                        </div>
                        <UBadge
                            v-if="act.done"
                            label="Done"
                            color="success"
                            variant="soft"
                            size="xs"
                        />
                    </li>
                </ul>
            </UCard>

            <!-- Upcoming activities -->
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
                <div v-if="pending" class="space-y-3">
                    <div v-for="i in 4" :key="i" class="flex items-center gap-3">
                        <USkeleton class="size-7 rounded-full" />
                        <div class="flex-1 space-y-1.5">
                            <USkeleton class="h-3.5 w-48" />
                            <USkeleton class="h-3 w-24" />
                        </div>
                    </div>
                </div>
                <div
                    v-else-if="!dashboard?.upcomingActivities?.length"
                    class="text-muted py-6 text-center text-sm"
                >
                    Nothing scheduled
                </div>
                <ul v-else class="divide-default divide-y">
                    <li
                        v-for="act in dashboard.upcomingActivities"
                        :key="act.id"
                        class="flex items-start gap-3 py-2.5"
                    >
                        <div class="bg-primary/10 mt-0.5 shrink-0 rounded-full p-1.5">
                            <UIcon :name="activityIcon(act.type)" class="text-primary size-3.5" />
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
    </div>
</template>
