<script lang="ts">
/** One row of an {@link AppBarList}. */
export interface BarListItem {
    /** Stable key (falls back to the row index). */
    id?: string | number
    label: string
    /** Magnitude that drives the bar width. */
    value: number
    /** Text shown on the right (defaults to the raw `value`). */
    display?: string | number
    /** Optional muted text appended after the label. */
    secondary?: string
    /** Optional CSS color for the bar fill (e.g. a stage hex); else themed primary. */
    color?: string | null
}
</script>

<script setup lang="ts">
/**
 * Horizontal bar list for "by dimension" dashboard widgets (leads by stage,
 * revenue by source/type). Bars are sized relative to the largest `value`.
 */
const props = withDefaults(
    defineProps<{
        items: BarListItem[]
        loading?: boolean
        emptyMessage?: string
        /** Skeleton row count while loading. */
        rows?: number
    }>(),
    { emptyMessage: 'No data yet', rows: 4 }
)

const max = computed(() => Math.max(1, ...props.items.map((i) => i.value)))
function width(value: number) {
    return `${Math.max(2, (value / max.value) * 100)}%`
}
</script>

<template>
    <div v-if="loading" class="space-y-3">
        <div v-for="i in rows" :key="i" class="flex items-center gap-3">
            <USkeleton class="h-3.5 w-24" />
            <USkeleton class="h-4 flex-1" />
            <USkeleton class="h-3.5 w-10" />
        </div>
    </div>
    <div v-else-if="!items.length" class="text-muted py-6 text-center text-sm">
        {{ emptyMessage }}
    </div>
    <div v-else class="space-y-3">
        <div v-for="(item, idx) in items" :key="item.id ?? idx" class="flex items-center gap-3">
            <span class="w-28 shrink-0 truncate text-xs">
                <span class="text-muted">{{ item.label }}</span>
                <span v-if="item.secondary" class="text-dimmed"> · {{ item.secondary }}</span>
            </span>
            <div class="bg-muted h-1.5 flex-1 overflow-hidden rounded-full">
                <div
                    class="h-full rounded-full transition-all"
                    :class="item.color ? '' : 'bg-primary'"
                    :style="{
                        width: width(item.value),
                        ...(item.color ? { backgroundColor: item.color } : {}),
                    }"
                />
            </div>
            <span class="text-highlighted shrink-0 text-right text-sm font-semibold">
                {{ item.display ?? item.value }}
            </span>
        </div>
    </div>
</template>
