<script setup lang="ts">
/**
 * Dashboard stat tile: a tinted icon badge next to a value + label. Shows a
 * skeleton in place of the value while `loading`. When `trend` is supplied it
 * renders a period-over-period pill (▲/▼ + percent) on the right.
 */
const props = defineProps<{
    label: string
    icon: string
    value?: string | number
    /** Icon text-color class, e.g. "text-primary". */
    iconColor?: string
    /** Icon badge background class, e.g. "bg-primary/10". */
    iconBg?: string
    loading?: boolean
    /** Period-over-period change percent (e.g. 12.5 → "+12.5%"). Hidden when nullish. */
    trend?: number | null
    /** Treat an increase as bad (e.g. lost revenue/leads) — flips the trend color. */
    invertTrend?: boolean
}>()

const trend = computed(() => {
    const t = props.trend
    if (t == null) return null
    const rounded = Math.round(t * 10) / 10
    const good = props.invertTrend ? rounded < 0 : rounded > 0
    const bad = props.invertTrend ? rounded > 0 : rounded < 0
    return {
        text: `${rounded > 0 ? '+' : ''}${rounded}%`,
        icon:
            rounded > 0
                ? 'i-tabler-trending-up'
                : rounded < 0
                  ? 'i-tabler-trending-down'
                  : 'i-tabler-minus',
        class: good ? 'text-success' : bad ? 'text-error' : 'text-muted',
    }
})
</script>

<template>
    <UCard>
        <div class="flex items-center gap-3">
            <div class="shrink-0 rounded-lg p-2" :class="iconBg">
                <UIcon :name="icon" class="size-5" :class="iconColor" />
            </div>
            <div class="min-w-0 flex-1">
                <USkeleton v-if="loading" class="mb-1 h-6 w-16" />
                <p v-else class="text-highlighted text-2xl font-bold">{{ value }}</p>
                <p class="text-muted text-xs">{{ label }}</p>
            </div>
            <div
                v-if="!loading && trend"
                class="flex shrink-0 items-center gap-0.5 text-xs font-medium"
                :class="trend.class"
                :title="`vs previous period`"
            >
                <UIcon :name="trend.icon" class="size-3.5" />
                {{ trend.text }}
            </div>
        </div>
    </UCard>
</template>
