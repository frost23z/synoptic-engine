<script setup lang="ts">
/** Standard list/detail page header: title + optional subtitle on the left, actions slot on the right. */
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
    <div class="flex items-center justify-between gap-4">
        <div>
            <h2 class="text-highlighted text-xl font-semibold">{{ title }}</h2>
            <p v-if="subtitle" class="text-muted text-sm">{{ subtitle }}</p>
        </div>
        <div v-if="$slots.actions" class="flex items-center gap-2">
            <slot name="actions" />
        </div>
    </div>
</template>
