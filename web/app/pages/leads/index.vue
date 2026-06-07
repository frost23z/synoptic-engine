<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { KanbanStageGroup, LeadResponse } from '~/types/leads'
import type { PipelineResponse } from '~/types/pipelines'
import type { StageResponse } from '~/types/settings'
import { LEAD_STATUS_COLOR, LEAD_STATUS_LABEL } from '~/types/leads'

definePageMeta({ title: 'Leads' })
useHead({ title: 'Leads — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatCurrency, formatDate } = useFormatters()
const { can } = usePermissions()
const { downloadBlob } = useDownload()

const exporting = ref(false)
async function exportCsv() {
    exporting.value = true
    try {
        await downloadBlob('/api/leads/export', 'leads.csv')
    } catch {
        toast.add({ title: 'Export failed', color: 'error' })
    } finally {
        exporting.value = false
    }
}

// ── View + selection ────────────────────────────────────────────────────
const view = ref<'list' | 'kanban'>('list')
const selectedPipelineId = ref<string | undefined>()

const { selected, selectAll, clearAll, count } = useMassSelect()
const massDeleting = ref(false)
const massUpdating = ref(false)
const massStageId = ref('')

// ── List data ────────────────────────────────────────────────────────────
const {
    page,
    search,
    items: leads,
    total: totalLeads,
    pending: listPending,
    refresh: refreshList,
} = await usePaginatedList<LeadResponse>('/api/leads', {
    key: 'leads-list',
    params: () => ({ pipelineId: selectedPipelineId.value }),
})

// ── Saved views (datagrid filters) ────────────────────────────────────────
const appliedFilter = computed(() => ({
    search: search.value || undefined,
    pipelineId: selectedPipelineId.value,
}))
function applySavedFilter(applied: Record<string, unknown>) {
    search.value = typeof applied.search === 'string' ? applied.search : ''
    selectedPipelineId.value =
        typeof applied.pipelineId === 'string' ? applied.pipelineId : undefined
    page.value = 1
}

// ── Pipelines (kanban selector + default) ─────────────────────────────────
const { data: pipelines } = await useAsyncData<PipelineResponse[]>('pipelines', () =>
    api<PipelineResponse[]>('/api/pipelines')
)
const pipelineOptions = computed(
    () => pipelines.value?.map((p) => ({ label: p.name, value: p.id })) ?? []
)
watchEffect(() => {
    if (pipelines.value?.length && !selectedPipelineId.value) {
        selectedPipelineId.value = pipelines.value[0]?.id
    }
})

// Load stages when pipeline changes for the mass-move select
const massStageList = ref<StageResponse[]>([])
watch(
    selectedPipelineId,
    async (id) => {
        if (!id) {
            massStageList.value = []
            return
        }
        massStageList.value = await api<StageResponse[]>(`/api/pipelines/${id}/stages`).catch(
            () => []
        )
    },
    { immediate: true }
)
const stageOptions = computed(() =>
    massStageList.value.map((s) => ({ label: s.name, value: s.id }))
)

// ── Kanban data ──────────────────────────────────────────────────────────
const kanbanQueryKey = computed(() => ['leads-kanban', selectedPipelineId.value])
const {
    data: kanbanData,
    pending: kanbanPending,
    refresh: refreshKanban,
} = await useAsyncData<KanbanStageGroup[]>(
    () => kanbanQueryKey.value.join('-'),
    () => {
        if (!selectedPipelineId.value) return Promise.resolve([])
        return api<KanbanStageGroup[]>('/api/leads/kanban', {
            params: { pipelineId: selectedPipelineId.value },
        })
    },
    { watch: [kanbanQueryKey] }
)
const kanbanGroups = computed(() => kanbanData.value ?? [])

// ── Mass ops ──────────────────────────────────────────────────────────────
async function massDelete() {
    if (!selected.value.length) return
    massDeleting.value = true
    try {
        await api('/api/leads/mass-destroy', { method: 'POST', body: { ids: selected.value } })
        toast.add({ title: `${count.value} leads deleted`, color: 'success' })
        clearAll()
        refreshList()
        if (view.value === 'kanban') refreshKanban()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

async function massUpdateStage() {
    if (!selected.value.length || !massStageId.value) return
    massUpdating.value = true
    try {
        await api('/api/leads/mass-update', {
            method: 'POST',
            body: { ids: selected.value, stageId: massStageId.value },
        })
        toast.add({ title: `${count.value} leads updated`, color: 'success' })
        clearAll()
        refreshList()
    } catch {
        toast.add({ title: 'Mass update failed', color: 'error' })
    } finally {
        massUpdating.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: leadToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<LeadResponse>({
    endpoint: (l) => `/api/leads/${l.id}`,
    successMessage: 'Lead deleted',
    onDeleted: () => {
        refreshList()
        if (view.value === 'kanban') refreshKanban()
    },
})

// ── Table columns + row actions ───────────────────────────────────────────
const columns: TableColumn<LeadResponse>[] = [
    { accessorKey: 'title', header: 'Title', meta: { class: { td: 'font-medium' } } },
    { accessorKey: 'status', header: 'Status' },
    { accessorKey: 'amount', header: 'Amount' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(lead: LeadResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [{ label: 'View', icon: 'i-tabler-eye', onSelect: () => router.push(`/leads/${lead.id}`) }],
    ]
    if (can('leads.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(lead),
            },
        ])
    }
    return items
}

// ── Kanban drag-and-drop ──────────────────────────────────────────────────
const dragging = ref<{ leadId: string; fromStageId: string } | null>(null)
function onDragStart(leadId: string, stageId: string) {
    dragging.value = { leadId, fromStageId: stageId }
}
async function onDrop(toStageId: string) {
    if (!dragging.value || dragging.value.fromStageId === toStageId) {
        dragging.value = null
        return
    }
    try {
        await api(`/api/leads/${dragging.value.leadId}/stage`, {
            method: 'PATCH',
            body: { stageId: toStageId },
        })
        refreshKanban()
        toast.add({ title: 'Stage updated', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to move lead', color: 'error' })
    } finally {
        dragging.value = null
    }
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Leads" :subtitle="`${totalLeads.toLocaleString()} total`">
            <template #actions>
                <UButtonGroup>
                    <UButton
                        icon="i-tabler-list"
                        color="neutral"
                        :variant="view === 'list' ? 'soft' : 'ghost'"
                        @click="view = 'list'"
                    />
                    <UButton
                        icon="i-tabler-layout-kanban"
                        color="neutral"
                        :variant="view === 'kanban' ? 'soft' : 'ghost'"
                        @click="view = 'kanban'"
                    />
                </UButtonGroup>
                <UButton
                    v-if="can('leads.view')"
                    icon="i-tabler-download"
                    label="Export"
                    color="neutral"
                    variant="outline"
                    :loading="exporting"
                    @click="exportCsv"
                />
                <UButton
                    v-if="can('leads.create')"
                    icon="i-tabler-plus"
                    label="New Lead"
                    to="/leads/create"
                />
            </template>
        </AppPageHeader>

        <!-- Filters -->
        <div class="flex flex-wrap items-center gap-3">
            <UInput
                v-model="search"
                placeholder="Search leads…"
                icon="i-tabler-search"
                class="w-56"
            />
            <USelect
                v-model="selectedPipelineId"
                :items="pipelineOptions"
                placeholder="Pipeline"
                class="w-44"
            />
            <AppSavedFilters src="leads" :applied="appliedFilter" @apply="applySavedFilter" />
        </div>

        <!-- LIST VIEW -->
        <template v-if="view === 'list'">
            <AppMassActionBar :count="count" @clear="clearAll">
                <USelect
                    v-model="massStageId"
                    :items="stageOptions"
                    placeholder="Move to stage…"
                    class="w-44"
                />
                <UButton
                    label="Move"
                    size="sm"
                    :loading="massUpdating"
                    :disabled="!massStageId"
                    @click="massUpdateStage"
                />
                <UButton
                    icon="i-tabler-trash"
                    label="Delete"
                    size="sm"
                    color="error"
                    variant="soft"
                    :loading="massDeleting"
                    @click="massDelete"
                />
            </AppMassActionBar>

            <AppListTable
                :rows="leads"
                :columns="columns"
                :loading="listPending"
                selectable
                :selected="selected"
                @update:selected="selectAll"
            >
                <template #title-cell="{ row }">
                    <NuxtLink
                        :to="`/leads/${row.original.id}`"
                        class="text-primary hover:underline"
                    >
                        {{ row.original.title }}
                    </NuxtLink>
                </template>
                <template #status-cell="{ row }">
                    <UBadge
                        :label="LEAD_STATUS_LABEL[row.original.status]"
                        :color="LEAD_STATUS_COLOR[row.original.status]"
                        variant="soft"
                        size="sm"
                    />
                </template>
                <template #amount-cell="{ row }">
                    <span class="font-medium">{{ formatCurrency(row.original.amount) }}</span>
                </template>
                <template #createdAt-cell="{ row }">
                    <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
                </template>
                <template #actions-cell="{ row }">
                    <AppRowActions :items="rowActions(row.original)" />
                </template>
                <template #empty>
                    <AppEmptyState icon="i-tabler-target-off" message="No leads found">
                        <NuxtLink to="/leads/create">
                            <UButton size="sm" variant="outline" label="Create first lead" />
                        </NuxtLink>
                    </AppEmptyState>
                </template>
            </AppListTable>

            <AppPagination v-model:page="page" :total="totalLeads" />
        </template>

        <!-- KANBAN VIEW -->
        <template v-else>
            <USelect
                v-model="selectedPipelineId"
                :items="pipelineOptions"
                placeholder="Select pipeline"
                class="w-56"
            />

            <div v-if="kanbanPending" class="flex gap-4 overflow-x-auto pb-4">
                <div v-for="i in 4" :key="i" class="w-64 shrink-0 space-y-2">
                    <USkeleton class="h-8 w-full" />
                    <USkeleton v-for="j in 3" :key="j" class="h-20 w-full" />
                </div>
            </div>

            <div v-else class="flex gap-3 overflow-x-auto pb-6">
                <div
                    v-for="group in kanbanGroups"
                    :key="group.stage.id"
                    class="bg-muted/40 border-default w-64 shrink-0 space-y-2 rounded-xl border p-3"
                    @dragover.prevent
                    @drop="onDrop(group.stage.id)"
                >
                    <div class="flex items-center justify-between px-1 py-0.5">
                        <span class="text-highlighted text-sm font-semibold">
                            {{ group.stage.name }}
                        </span>
                        <div class="flex items-center gap-1.5">
                            <UBadge
                                :label="String(group.leads.length)"
                                color="neutral"
                                variant="soft"
                                size="xs"
                            />
                        </div>
                    </div>
                    <p v-if="group.totalAmount" class="text-muted px-1 text-xs">
                        {{ formatCurrency(group.totalAmount) }}
                    </p>

                    <div class="min-h-16 space-y-2">
                        <UCard
                            v-for="lead in group.leads"
                            :key="lead.id"
                            draggable="true"
                            class="cursor-grab active:cursor-grabbing"
                            :ui="{ body: 'p-3' }"
                            @dragstart="onDragStart(lead.id, group.stage.id)"
                        >
                            <NuxtLink :to="`/leads/${lead.id}`" class="block space-y-1.5">
                                <p class="text-highlighted line-clamp-2 text-sm font-medium">
                                    {{ lead.title }}
                                </p>
                                <div class="flex items-center justify-between">
                                    <UBadge
                                        :label="LEAD_STATUS_LABEL[lead.status]"
                                        :color="LEAD_STATUS_COLOR[lead.status]"
                                        variant="soft"
                                        size="xs"
                                    />
                                    <span
                                        v-if="lead.amount"
                                        class="text-primary text-xs font-semibold"
                                    >
                                        {{ formatCurrency(lead.amount) }}
                                    </span>
                                </div>
                            </NuxtLink>
                        </UCard>
                    </div>
                </div>
            </div>
        </template>

        <!-- Delete confirmation modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Lead"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ leadToDelete?.title }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
