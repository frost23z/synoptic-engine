<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'

definePageMeta({ title: 'Imports' })
useHead({ title: 'Imports — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

interface DataImportResponse {
    id: string
    name: string
    entityType: string
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
    errorCount: number
    successCount: number
    createdAt: string
    updatedAt: string
}

interface DataImportStatsResponse {
    id: string
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
    errorCount: number
    successCount: number
    errors: Record<string, unknown>[]
}

const ENTITY_TYPES = ['Person', 'Lead', 'Product']

const STATUS_COLOR: Record<string, 'neutral' | 'warning' | 'success' | 'error'> = {
    PENDING: 'neutral',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'error',
}

const {
    data: imports,
    pending,
    refresh,
} = await useAsyncData<DataImportResponse[]>('data-imports', () =>
    api<DataImportResponse[]>('/api/settings/imports')
)

// ── Upload ────────────────────────────────────────────────────────────────
const uploadOpen = ref(false)
const uploading = ref(false)
const selectedEntityType = ref('Person')
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)

function openUpload() {
    selectedEntityType.value = 'Person'
    selectedFile.value = null
    uploadOpen.value = true
}

function onFileChange(e: Event) {
    const target = e.target as HTMLInputElement
    selectedFile.value = target.files?.[0] ?? null
}

async function submitUpload() {
    if (!selectedFile.value) return
    uploading.value = true
    try {
        const formData = new FormData()
        formData.append('file', selectedFile.value)
        await api(`/api/settings/imports?entityType=${selectedEntityType.value}`, {
            method: 'POST',
            body: formData,
        })
        toast.add({ title: 'Import created', color: 'success' })
        uploadOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Upload failed', description: e?.data?.message, color: 'error' })
    } finally {
        uploading.value = false
    }
}

// ── Start import ──────────────────────────────────────────────────────────
const starting = ref<string | null>(null)

async function startImport(imp: DataImportResponse) {
    starting.value = imp.id
    try {
        await api(`/api/settings/imports/${imp.id}/start`, { method: 'POST' })
        toast.add({ title: 'Import started', color: 'success' })
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to start', description: e?.data?.message, color: 'error' })
    } finally {
        starting.value = null
    }
}

// ── Stats modal ───────────────────────────────────────────────────────────
const statsOpen = ref(false)
const statsData = ref<DataImportStatsResponse | null>(null)
const statsPending = ref(false)

async function openStats(imp: DataImportResponse) {
    statsOpen.value = true
    statsData.value = null
    statsPending.value = true
    try {
        statsData.value = await api<DataImportStatsResponse>(
            `/api/settings/imports/${imp.id}/stats`
        )
    } catch {
        toast.add({ title: 'Failed to load stats', color: 'error' })
        statsOpen.value = false
    } finally {
        statsPending.value = false
    }
}

// ── Download errors ───────────────────────────────────────────────────────
async function downloadErrors(imp: DataImportResponse) {
    try {
        const blob = await $fetch<Blob>(`/api/settings/imports/${imp.id}/download-errors`, {
            headers: { Accept: 'text/csv' },
            responseType: 'blob',
        })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `import-errors-${imp.id}.csv`
        a.click()
        URL.revokeObjectURL(url)
    } catch {
        toast.add({ title: 'Failed to download errors', color: 'error' })
    }
}

// ── Download sample ───────────────────────────────────────────────────────
async function downloadSample(entityType: string) {
    try {
        const blob = await $fetch<Blob>(`/api/settings/imports/sample/${entityType}`, {
            headers: { Accept: 'text/csv' },
            responseType: 'blob',
        })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `sample-${entityType.toLowerCase()}.csv`
        a.click()
        URL.revokeObjectURL(url)
    } catch {
        toast.add({ title: 'Failed to download sample', color: 'error' })
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: importToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<DataImportResponse>({
    endpoint: (i) => `/api/settings/imports/${i.id}`,
    successMessage: 'Import deleted',
    onDeleted: refresh,
})

// ── Table ─────────────────────────────────────────────────────────────────
const columns: TableColumn<DataImportResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'entityType', header: 'Entity' },
    { id: 'status', header: 'Status' },
    { id: 'results', header: 'Results' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

const sampleItems: DropdownMenuItem[][] = [
    ENTITY_TYPES.map((t) => ({
        label: `${t} sample`,
        icon: 'i-tabler-file-download',
        onSelect: () => downloadSample(t),
    })),
]

function rowActions(imp: DataImportResponse): DropdownMenuItem[][] {
    const primary: DropdownMenuItem[] = []
    if (imp.status === 'PENDING') {
        primary.push({
            label: 'Start Import',
            icon: 'i-tabler-player-play',
            loading: starting.value === imp.id,
            onSelect: () => startImport(imp),
        })
    }
    primary.push({
        label: 'View Stats',
        icon: 'i-tabler-chart-bar',
        onSelect: () => openStats(imp),
    })
    if (imp.errorCount > 0) {
        primary.push({
            label: 'Download Errors',
            icon: 'i-tabler-download',
            onSelect: () => downloadErrors(imp),
        })
    }
    return [
        primary,
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(imp),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Data Imports"
            subtitle="Import contacts, leads, and products from CSV files"
        >
            <template #actions>
                <UDropdownMenu :items="sampleItems">
                    <UButton
                        icon="i-tabler-file-download"
                        label="Sample CSV"
                        color="neutral"
                        variant="outline"
                    />
                </UDropdownMenu>
                <UButton
                    v-if="can('imports.create')"
                    icon="i-tabler-upload"
                    label="Upload CSV"
                    @click="openUpload"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="imports ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <span class="font-medium">{{ row.original.name }}</span>
            </template>
            <template #entityType-cell="{ row }">
                <UBadge :label="row.original.entityType" color="neutral" variant="soft" size="sm" />
            </template>
            <template #status-cell="{ row }">
                <UBadge
                    :label="row.original.status"
                    :color="STATUS_COLOR[row.original.status]"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #results-cell="{ row }">
                <div class="flex items-center gap-2 text-sm">
                    <span class="text-success">{{ row.original.successCount }} ok</span>
                    <span v-if="row.original.errorCount > 0" class="text-error"
                        >{{ row.original.errorCount }} err</span
                    >
                    <span v-else class="text-muted">0 err</span>
                </div>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-file-import" message="No imports yet" />
            </template>
        </AppListTable>

        <!-- Upload modal -->
        <AppConfirmModal
            v-model:open="uploadOpen"
            title="Upload CSV"
            confirm-label="Upload"
            :loading="uploading"
            :confirm-disabled="!selectedFile"
            @confirm="submitUpload"
        >
            <div class="space-y-4">
                <UFormField label="Entity Type" required>
                    <USelect
                        v-model="selectedEntityType"
                        :items="ENTITY_TYPES.map((t) => ({ label: t, value: t }))"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="CSV File" required>
                    <div
                        class="border-default hover:border-primary flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed p-6 transition-colors"
                        @click="fileInput?.click()"
                    >
                        <UIcon name="i-tabler-upload" class="text-muted size-8" />
                        <p v-if="selectedFile" class="text-highlighted text-sm font-medium">
                            {{ selectedFile.name }}
                        </p>
                        <p v-else class="text-muted text-sm">Click to select a CSV file</p>
                        <input
                            ref="fileInput"
                            type="file"
                            accept=".csv"
                            class="hidden"
                            @change="onFileChange"
                        />
                    </div>
                </UFormField>
            </div>
        </AppConfirmModal>

        <!-- Stats modal (view-only) -->
        <UModal v-model:open="statsOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Import Stats</p>
                    </template>
                    <div v-if="statsPending" class="space-y-3 py-4">
                        <USkeleton class="h-4 w-3/4" />
                        <USkeleton class="h-4 w-1/2" />
                    </div>
                    <template v-else-if="statsData">
                        <div class="grid grid-cols-3 gap-3">
                            <div class="border-default rounded-lg border p-3 text-center">
                                <p class="text-muted text-xs">Status</p>
                                <UBadge
                                    :label="statsData.status"
                                    :color="STATUS_COLOR[statsData.status]"
                                    variant="soft"
                                    size="sm"
                                    class="mt-1"
                                />
                            </div>
                            <div class="border-default rounded-lg border p-3 text-center">
                                <p class="text-muted text-xs">Succeeded</p>
                                <p class="text-success mt-1 text-xl font-semibold">
                                    {{ statsData.successCount }}
                                </p>
                            </div>
                            <div class="border-default rounded-lg border p-3 text-center">
                                <p class="text-muted text-xs">Failed</p>
                                <p class="text-error mt-1 text-xl font-semibold">
                                    {{ statsData.errorCount }}
                                </p>
                            </div>
                        </div>

                        <div
                            v-if="statsData.errors && statsData.errors.length > 0"
                            class="mt-4 space-y-2"
                        >
                            <p class="text-muted text-xs font-semibold uppercase">
                                Errors (first {{ Math.min(statsData.errors.length, 10) }})
                            </p>
                            <div class="border-default max-h-48 overflow-y-auto rounded-lg border">
                                <div
                                    v-for="(err, idx) in statsData.errors.slice(0, 10)"
                                    :key="idx"
                                    class="border-default border-b px-3 py-2 font-mono text-xs last:border-0"
                                >
                                    {{ JSON.stringify(err) }}
                                </div>
                            </div>
                        </div>
                    </template>
                    <template #footer>
                        <div class="flex justify-end">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Close"
                                @click="statsOpen = false"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Import"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ importToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
