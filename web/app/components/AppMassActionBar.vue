<script setup lang="ts">
/** Bulk-action bar shown when rows are selected. Renders only when `count > 0`. */
defineProps<{ count: number }>()
defineEmits<{ clear: [] }>()
</script>

<template>
    <div
        v-if="count > 0"
        class="bg-default border-default flex items-center gap-3 rounded-lg border px-4 py-2"
    >
        <span class="text-muted text-sm">{{ count }} selected</span>
        <slot />
        <UButton label="Clear" size="sm" color="neutral" variant="ghost" @click="$emit('clear')" />
    </div>
</template>
