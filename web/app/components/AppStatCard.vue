<script setup lang="ts">
/**
 * Dashboard stat tile: a tinted icon badge next to a value + label. Shows a
 * skeleton in place of the value while `loading`.
 */
defineProps<{
    label: string
    icon: string
    value?: string | number
    /** Icon text-color class, e.g. "text-primary". */
    iconColor?: string
    /** Icon badge background class, e.g. "bg-primary/10". */
    iconBg?: string
    loading?: boolean
}>()
</script>

<template>
    <UCard>
        <div class="flex items-center gap-3">
            <div class="shrink-0 rounded-lg p-2" :class="iconBg">
                <UIcon :name="icon" class="size-5" :class="iconColor" />
            </div>
            <div class="min-w-0">
                <USkeleton v-if="loading" class="mb-1 h-6 w-16" />
                <p v-else class="text-highlighted text-2xl font-bold">{{ value }}</p>
                <p class="text-muted text-xs">{{ label }}</p>
            </div>
        </div>
    </UCard>
</template>
