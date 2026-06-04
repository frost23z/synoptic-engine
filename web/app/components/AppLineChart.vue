<script lang="ts">
/** One line in an {@link AppLineChart}. */
export interface LineChartSeries {
    label: string
    /** Tailwind text-color class — the stroke uses `currentColor`, e.g. "text-primary". */
    color: string
    /** Y-values, aligned index-for-index with the chart `labels`. */
    data: number[]
}
</script>

<script setup lang="ts">
/**
 * Minimal dependency-free multi-series line chart (SVG). Series share the same
 * x-axis `labels`; each stroke inherits its series' Tailwind text color via
 * `currentColor`, so the chart stays theme-aware. Intended for compact dashboard
 * trend widgets, not heavy analytics.
 */
const props = withDefaults(
    defineProps<{
        series: LineChartSeries[]
        labels: string[]
        loading?: boolean
        emptyMessage?: string
        /** Chart body height in px. */
        height?: number
    }>(),
    { emptyMessage: 'No data yet', height: 180 }
)

const VIEW_W = 100
const VIEW_H = 100
const GRID_LINES = [0, 25, 50, 75, 100]

const max = computed(() => Math.max(1, ...props.series.flatMap((s) => s.data)))

const hasData = computed(
    () => props.labels.length > 0 && props.series.some((s) => s.data.some((v) => v > 0))
)

function points(data: number[]): string {
    const n = data.length
    if (n === 0) return ''
    return data
        .map((v, i) => {
            const x = n === 1 ? VIEW_W / 2 : (i / (n - 1)) * VIEW_W
            const y = VIEW_H - (v / max.value) * VIEW_H
            return `${x.toFixed(2)},${y.toFixed(2)}`
        })
        .join(' ')
}
</script>

<template>
    <div>
        <div v-if="!loading" class="mb-3 flex flex-wrap items-center gap-x-4 gap-y-1">
            <div v-for="s in series" :key="s.label" class="flex items-center gap-1.5 text-xs">
                <span
                    class="h-0.5 w-3 rounded-full"
                    :class="s.color"
                    style="background-color: currentColor"
                />
                <span class="text-muted">{{ s.label }}</span>
            </div>
        </div>

        <USkeleton v-if="loading" class="w-full" :style="{ height: `${height}px` }" />
        <div
            v-else-if="!hasData"
            class="text-muted flex items-center justify-center text-sm"
            :style="{ height: `${height}px` }"
        >
            {{ emptyMessage }}
        </div>
        <template v-else>
            <div class="relative" :style="{ height: `${height}px` }">
                <span class="text-dimmed absolute top-0 left-0 text-[10px]">{{ max }}</span>
                <span class="text-dimmed absolute bottom-0 left-0 text-[10px]">0</span>
                <svg
                    class="h-full w-full"
                    :viewBox="`0 0 ${VIEW_W} ${VIEW_H}`"
                    preserveAspectRatio="none"
                >
                    <g class="text-muted" stroke="currentColor" stroke-opacity="0.15">
                        <line
                            v-for="g in GRID_LINES"
                            :key="g"
                            x1="0"
                            :y1="g"
                            x2="100"
                            :y2="g"
                            stroke-width="0.5"
                            vector-effect="non-scaling-stroke"
                        />
                    </g>
                    <polyline
                        v-for="s in series"
                        :key="s.label"
                        :class="s.color"
                        :points="points(s.data)"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        vector-effect="non-scaling-stroke"
                    />
                </svg>
            </div>
            <div v-if="labels.length" class="text-dimmed mt-1.5 flex justify-between text-[10px]">
                <span>{{ labels[0] }}</span>
                <span v-if="labels.length > 1">{{ labels[labels.length - 1] }}</span>
            </div>
        </template>
    </div>
</template>
