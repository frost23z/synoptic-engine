<script setup lang="ts">
/** Empty-state block for tables/lists: icon + message + optional CTA (default slot). */
defineProps<{ icon?: string; message?: string }>()
</script>

<template>
    <div class="space-y-2 py-12 text-center">
        <UIcon v-if="icon" :name="icon" class="text-muted mx-auto size-10" />
        <p class="text-muted text-sm">{{ message ?? 'Nothing here yet' }}</p>
        <slot />
    </div>
</template>
