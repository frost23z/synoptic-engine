<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { KanbanStageGroup, LeadResponse, LeadsPage } from '~/types/leads'
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

// ── View + filters ──────────────────────────────────────────────────────
const view = ref<'list' | 'kanban'>('list')
const page = ref(1)
const PAGE_SIZE = 20
const search = ref('')
const selectedPipelineId = ref<string | undefined>()

const { selected, isSelected, toggle, selectAll, clearAll, hasSelection, count } = useMassSelect()
const massDeleting = ref(false)
const massUpdating = ref(false)
const massStageId = ref('')

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

async function massDelete() {
    if (!hasSelection.value) return
    massDeleting.value = true
    try {
        await api('/api/leads/mass-destroy', {
            method: 'POST',
            body: { ids: selected.value },
        })
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
    if (!hasSelection.value || !massStageId.value) return
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

const debouncedSearch = refDebounced(search, 300)

// ── Pipelines (for kanban pipeline selector) ─────────────────────────────
const { data: pipelines } = await useAsyncData<PipelineResponse[]>('pipelines', () =>
    api<PipelineResponse[]>('/api/pipelines')
)

const pipelineOptions = computed(
    () => pipelines.value?.map((p) => ({ label: p.name, value: p.id })) ?? []
)

// Default to first pipeline
watchEffect(() => {
    if (pipelines.value?.length && !selectedPipelineId.value) {
        selectedPipelineId.value = pipelines.value[0]?.id
    }
})

// ── List view data ─────────────────────────────────────────────────────────
const listQueryKey = computed(() => [
    'leads-list',
    page.value,
    debouncedSearch.value,
    selectedPipelineId.value,
])

const {
    data: leadsPage,
    pending: listPending,
    refresh: refreshList,
} = await useAsyncData<LeadsPage>(
    () => listQueryKey.value.join('-'),
    () => {
        const params: Record<string, string | number> = {
            page: page.value - 1,
            size: PAGE_SIZE,
        }
        if (debouncedSearch.value) params.q = debouncedSearch.value
        if (selectedPipelineId.value) params.pipelineId = selectedPipelineId.value
        return api<LeadsPage>('/api/leads', { params })
    },
    { watch: [listQueryKey] }
)

// ── Kanban data ─────────────────────────────────────────────────────────
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

const leads = computed(() => leadsPage.value?.content ?? [])
const totalLeads = computed(() => leadsPage.value?.totalElements ?? 0)
const kanbanGroups = computed(() => kanbanData.value ?? [])

// ── Table columns ──────────────────────────────────────────────────────────
const columns: TableColumn<LeadResponse>[] = [
    {
        id: 'select',
        header: '',
        meta: { class: { th: 'w-8', td: 'w-8' } },
    },
    {
        accessorKey: 'title',
        header: 'Title',
        meta: { class: { td: 'font-medium' } },
    },
    {
        accessorKey: 'status',
        header: 'Status',
    },
    {
        accessorKey: 'amount',
        header: 'Amount',
    },
    {
        accessorKey: 'createdAt',
        header: 'Created',
    },
    {
        id: 'actions',
        header: '',
        meta: { class: { th: 'w-10', td: 'w-10' } },
    },
]

// ── Row actions ─────────────────────────────────────────────────────────
function rowActions(lead: LeadResponse) {
    const items: object[][] = [
        [{ label: 'View', icon: 'i-tabler-eye', click: () => router.push(`/leads/${lead.id}`) }],
    ]
    if (can('leads.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => openDeleteModal(lead),
            },
        ])
    }
    return items
}

// ── Delete ─────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const leadToDelete = ref<LeadResponse | null>(null)
const deleting = ref(false)

function openDeleteModal(lead: LeadResponse) {
    leadToDelete.value = lead
    deleteOpen.value = true
}

async function confirmDelete() {
    if (!leadToDelete.value) return
    deleting.value = true
    try {
        await api(`/api/leads/${leadToDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Lead deleted', color: 'success' })
        deleteOpen.value = false
        refreshList()
        if (view.value === 'kanban') refreshKanban()
    } catch {
        toast.add({ title: 'Failed to delete lead', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Kanban drag-and-drop ───────────────────────────────────────────────
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
        <!-- Page header -->
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Leads</h2>
                <p class="text-muted text-sm">{{ totalLeads.toLocaleString() }} total</p>
            </div>
            <div class="flex items-center gap-2">
                <!-- View toggle -->
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
                <NuxtLink v-if="can('leads.create')" to="/leads/create">
                    <UButton icon="i-tabler-plus" label="New Lead" />
                </NuxtLink>
            </div>
        </div>

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
        </div>

        <!-- LIST VIEW -->
        <template v-if="view === 'list'">
            <!-- Mass-ops action bar -->
            <div
                v-if="hasSelection"
                class="bg-default border-default flex items-center gap-3 rounded-lg border px-4 py-2"
            >
                <span class="text-muted text-sm">{{ count }} selected</span>
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
                <UButton
                    label="Clear"
                    size="sm"
                    color="neutral"
                    variant="ghost"
                    @click="clearAll"
                />
            </div>

            <UCard :ui="{ body: 'p-0' }">
                <UTable :data="leads" :columns="columns" :loading="listPending" sticky>
                    <!-- Select header -->
                    <template #select-header>
                        <UCheckbox
                            :checked="leads.length > 0 && selected.length === leads.length"
                            :indeterminate="selected.length > 0 && selected.length < leads.length"
                            @change="
                                leads.length === selected.length
                                    ? clearAll()
                                    : selectAll(leads.map((l) => l.id))
                            "
                        />
                    </template>
                    <template #select-cell="{ row }">
                        <UCheckbox
                            :checked="isSelected(row.original.id)"
                            @change="toggle(row.original.id)"
                        />
                    </template>

                    <!-- Title cell -->
                    <template #title-cell="{ row }">
                        <NuxtLink
                            :to="`/leads/${row.original.id}`"
                            class="text-primary hover:underline"
                        >
                            {{ row.original.title }}
                        </NuxtLink>
                    </template>

                    <!-- Status cell -->
                    <template #status-cell="{ row }">
                        <UBadge
                            :label="LEAD_STATUS_LABEL[row.original.status]"
                            :color="LEAD_STATUS_COLOR[row.original.status]"
                            variant="soft"
                            size="sm"
                        />
                    </template>

                    <!-- Amount cell -->
                    <template #amount-cell="{ row }">
                        <span class="font-medium">{{ formatCurrency(row.original.amount) }}</span>
                    </template>

                    <!-- Created cell -->
                    <template #createdAt-cell="{ row }">
                        <span class="text-muted text-sm">
                            {{ formatDate(row.original.createdAt) }}
                        </span>
                    </template>

                    <!-- Actions cell -->
                    <template #actions-cell="{ row }">
                        <UDropdownMenu :items="rowActions(row.original)">
                            <UButton
                                icon="i-tabler-dots-vertical"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                            />
                        </UDropdownMenu>
                    </template>

                    <!-- Empty state -->
                    <template #empty>
                        <div class="space-y-2 py-12 text-center">
                            <UIcon name="i-tabler-target-off" class="text-muted mx-auto size-10" />
                            <p class="text-muted text-sm">No leads found</p>
                            <NuxtLink to="/leads/create">
                                <UButton size="sm" variant="outline" label="Create first lead" />
                            </NuxtLink>
                        </div>
                    </template>
                </UTable>
            </UCard>

            <!-- Pagination -->
            <div v-if="totalLeads > PAGE_SIZE" class="flex justify-center">
                <UPagination
                    v-model:page="page"
                    :total="totalLeads"
                    :items-per-page="PAGE_SIZE"
                    :sibling-count="1"
                    show-edges
                />
            </div>
        </template>

        <!-- KANBAN VIEW -->
        <template v-else>
            <!-- Pipeline selector for kanban -->
            <USelect
                v-model="selectedPipelineId"
                :items="pipelineOptions"
                placeholder="Select pipeline"
                class="w-56"
            />

            <!-- Skeleton -->
            <div v-if="kanbanPending" class="flex gap-4 overflow-x-auto pb-4">
                <div v-for="i in 4" :key="i" class="w-64 shrink-0 space-y-2">
                    <USkeleton class="h-8 w-full" />
                    <USkeleton v-for="j in 3" :key="j" class="h-20 w-full" />
                </div>
            </div>

            <!-- Columns -->
            <div v-else class="flex gap-3 overflow-x-auto pb-6">
                <div
                    v-for="group in kanbanGroups"
                    :key="group.stage.id"
                    class="bg-muted/40 border-default w-64 shrink-0 space-y-2 rounded-xl border p-3"
                    @dragover.prevent
                    @drop="onDrop(group.stage.id)"
                >
                    <!-- Column header -->
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

                    <!-- Lead cards -->
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
        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Delete Lead</p>
                    </template>
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ leadToDelete?.title }}</strong
                        >? This cannot be undone.
                    </p>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="deleteOpen = false"
                            />
                            <UButton
                                color="error"
                                label="Delete"
                                :loading="deleting"
                                @click="confirmDelete"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>
    </div>
</template>
