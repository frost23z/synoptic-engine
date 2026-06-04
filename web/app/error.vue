<script setup lang="ts">
import type { NuxtError } from '#app'

/** Full-page fallback Nuxt renders for fatal/unhandled errors (and 404s). */
const props = defineProps<{ error: NuxtError }>()

const route = useRoute()
const isNotFound = computed(() => props.error.statusCode === 404)

const title = computed(() => (isNotFound.value ? 'Page not found' : 'Something went wrong'))
const message = computed(() =>
    isNotFound.value
        ? "The page you're looking for doesn't exist or may have moved."
        : props.error.statusMessage || props.error.message || 'An unexpected error occurred.'
)

function onHome() {
    clearError({ redirect: '/' })
}
function onRetry() {
    clearError({ redirect: route.fullPath })
}
</script>

<template>
    <div class="bg-default text-default flex min-h-screen items-center justify-center">
        <AppErrorState
            :status-code="error.statusCode"
            :title="title"
            :message="message"
            :icon="isNotFound ? 'i-tabler-map-search' : 'i-tabler-alert-triangle'"
            home
            :retryable="!isNotFound"
            @home="onHome"
            @retry="onRetry"
        />
    </div>
</template>
