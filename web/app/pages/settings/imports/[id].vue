<script setup lang="ts">
import type { DataImportResponse, DataImportStatsResponse, ImportStatus } from '~/types/settings'

definePageMeta({ title: 'Import' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const { downloadBlob } = useDownload()
const id = route.params.id as string

// Backend gates the import lifecycle (validate/link/index/start) + delete on imports.edit.
const canEdit = computed(() => can('imports.edit'))

const STATUS_COLOR: Record<ImportStatus, 'neutral' | 'warning' | 'success' | 'error'> = {
    PENDING: 'neutral',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'error',
}

const {
    data: imp,
    pending,
    refresh,
} = await useAsyncData<DataImportResponse>(`import-${id}`, () =>
    api<DataImportResponse>(`/api/settings/imports/${id}`)
)

useHead({
    title: computed(() => (imp.value?.name ? `${imp.value.name} — Synoptic` : 'Import — Synoptic')),
})

const { data: stats, refresh: refreshStats } = await useAsyncData<DataImportStatsResponse>(
    `import-${id}-stats`,
    () => api<DataImportStatsResponse>(`/api/settings/imports/${id}/stats`)
)

async function refreshAll() {
    await Promise.all([refresh(), refreshStats()])
}

// ── Lifecycle actions ──────────────────────────────────────────────────────
const busy = ref<string | null>(null)

async function runStep(step: 'validate' | 'link' | 'index-data' | 'start', label: string) {
    busy.value = step
    try {
        await api(`/api/settings/imports/${id}/${step}`, { method: 'POST' })
        toast.add({ title: `${label} complete`, color: 'success' })
        await refreshAll()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: `${label} failed`, description: e?.data?.message, color: 'error' })
    } finally {
        busy.value = null
    }
}

async function downloadErrors() {
    try {
        await downloadBlob(`/api/settings/imports/${id}/download-errors`, `import-errors-${id}.csv`)
    } catch {
        toast.add({ title: 'Failed to download errors', color: 'error' })
    }
}

// ── Delete ──────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<DataImportResponse>({
    endpoint: (i) => `/api/settings/imports/${i.id}`,
    successMessage: 'Import deleted',
    onDeleted: () => {
        router.push('/settings/imports')
    },
})
</script>

<template>
    <AppDetailLayout v-if="imp" to="/settings/imports" :title="imp.name">
        <template #subtitle>
            <div class="flex items-center gap-2">
                <UBadge :label="imp.entityType" color="neutral" variant="soft" size="sm" />
                <UBadge
                    :label="imp.status"
                    :color="STATUS_COLOR[imp.status]"
                    variant="soft"
                    size="sm"
                />
            </div>
        </template>
        <template #actions>
            <UButton
                v-if="canEdit"
                icon="i-tabler-trash"
                color="error"
                variant="outline"
                @click="promptDelete(imp)"
            />
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div class="space-y-6 lg:col-span-2">
                <!-- Lifecycle -->
                <UCard v-if="canEdit">
                    <template #header>
                        <p class="text-highlighted font-semibold">Pipeline</p>
                    </template>
                    <p class="text-muted mb-3 text-sm">
                        Run the import steps in order: validate the rows, link related records,
                        index the data, then start the import.
                    </p>
                    <div class="flex flex-wrap gap-2">
                        <UButton
                            label="Validate"
                            icon="i-tabler-checks"
                            color="neutral"
                            variant="outline"
                            :loading="busy === 'validate'"
                            :disabled="!!busy"
                            @click="runStep('validate', 'Validation')"
                        />
                        <UButton
                            label="Link"
                            icon="i-tabler-link"
                            color="neutral"
                            variant="outline"
                            :loading="busy === 'link'"
                            :disabled="!!busy"
                            @click="runStep('link', 'Linking')"
                        />
                        <UButton
                            label="Index"
                            icon="i-tabler-list-numbers"
                            color="neutral"
                            variant="outline"
                            :loading="busy === 'index-data'"
                            :disabled="!!busy"
                            @click="runStep('index-data', 'Indexing')"
                        />
                        <UButton
                            label="Start import"
                            icon="i-tabler-player-play"
                            :loading="busy === 'start'"
                            :disabled="!!busy"
                            @click="runStep('start', 'Import')"
                        />
                    </div>
                </UCard>

                <!-- Errors -->
                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">
                                Errors
                                <span class="text-muted font-normal">
                                    ({{ stats?.errorCount ?? imp.errorCount }})
                                </span>
                            </p>
                            <UButton
                                v-if="(stats?.errorCount ?? imp.errorCount) > 0"
                                label="Download CSV"
                                icon="i-tabler-download"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                @click="downloadErrors"
                            />
                        </div>
                    </template>
                    <div v-if="!stats?.errors?.length" class="text-muted py-6 text-center text-sm">
                        No errors recorded
                    </div>
                    <div
                        v-else
                        class="border-default divide-default max-h-96 divide-y overflow-y-auto rounded-lg border"
                    >
                        <div
                            v-for="(err, idx) in stats.errors"
                            :key="idx"
                            class="px-3 py-2 font-mono text-xs"
                        >
                            {{ JSON.stringify(err) }}
                        </div>
                    </div>
                </UCard>
            </div>

            <!-- Summary sidebar -->
            <div class="space-y-6">
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Summary</p>
                    </template>
                    <dl class="space-y-4 text-sm">
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Status</dt>
                            <dd>
                                <UBadge
                                    :label="imp.status"
                                    :color="STATUS_COLOR[imp.status]"
                                    variant="soft"
                                    size="sm"
                                />
                            </dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Succeeded</dt>
                            <dd class="text-success font-semibold">
                                {{ stats?.successCount ?? imp.successCount }}
                            </dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Failed</dt>
                            <dd class="text-error font-semibold">
                                {{ stats?.errorCount ?? imp.errorCount }}
                            </dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Created</dt>
                            <dd class="text-muted">{{ formatDate(imp.createdAt) }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Import"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ imp.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </AppDetailLayout>

    <div v-else-if="pending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <div class="grid grid-cols-3 gap-6">
            <USkeleton class="col-span-2 h-48 w-full" />
            <USkeleton class="h-40 w-full" />
        </div>
    </div>
</template>
