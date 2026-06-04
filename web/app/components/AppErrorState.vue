<script setup lang="ts">
/**
 * Friendly error panel: icon + (optional) status code + title + message, with
 * optional "Go to dashboard" / "Try again" actions. Used by the full-page
 * `error.vue` and the layout-level `NuxtErrorBoundary` fallback. The buttons
 * emit events so each caller can wire the right recovery (clearError, retry…).
 */
withDefaults(
    defineProps<{
        title?: string
        message?: string
        statusCode?: number | string
        icon?: string
        /** Show the "Try again" button (emits `retry`). */
        retryable?: boolean
        /** Show the "Go to dashboard" button (emits `home`). */
        home?: boolean
    }>(),
    { title: 'Something went wrong', icon: 'i-tabler-alert-triangle', retryable: true, home: false }
)
defineEmits<{ retry: []; home: [] }>()
</script>

<template>
    <div class="flex flex-col items-center justify-center gap-5 p-8 text-center">
        <div class="bg-muted rounded-full p-4">
            <UIcon :name="icon" class="text-primary size-9" />
        </div>
        <div class="space-y-1.5">
            <p v-if="statusCode != null" class="text-muted text-sm font-medium">{{ statusCode }}</p>
            <h2 class="text-highlighted text-xl font-semibold">{{ title }}</h2>
            <p v-if="message" class="text-muted mx-auto max-w-md text-sm">{{ message }}</p>
        </div>
        <div v-if="home || retryable" class="flex items-center gap-3">
            <UButton
                v-if="home"
                label="Go to dashboard"
                icon="i-tabler-home"
                color="primary"
                @click="$emit('home')"
            />
            <UButton
                v-if="retryable"
                label="Try again"
                icon="i-tabler-refresh"
                :color="home ? 'neutral' : 'primary'"
                :variant="home ? 'subtle' : 'solid'"
                @click="$emit('retry')"
            />
        </div>
    </div>
</template>
