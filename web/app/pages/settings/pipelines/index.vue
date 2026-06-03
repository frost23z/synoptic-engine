<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { PipelineResponse, StageResponse } from '~/types/settings'

definePageMeta({ title: 'Pipelines' })
useHead({ title: 'Pipelines — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const {
    data: pipelines,
    pending,
    refresh,
} = await useAsyncData<PipelineResponse[]>('settings-pipelines', () =>
    api<PipelineResponse[]>('/api/pipelines')
)

// ── Stages panel ──────────────────────────────────────────────────────────
const stagesOpen = ref(false)
const selectedPipeline = shallowRef<PipelineResponse | null>(null)
const stages = ref<StageResponse[]>([])
const stagesPending = ref(false)
const newStageName = ref('')
const addingStage = ref(false)

async function openStages(p: PipelineResponse) {
    selectedPipeline.value = p
    stagesOpen.value = true
    stagesPending.value = true
    try {
        stages.value = await api<StageResponse[]>(`/api/pipelines/${p.id}/stages`)
    } finally {
        stagesPending.value = false
    }
}

async function addStage() {
    if (!selectedPipeline.value || !newStageName.value.trim()) return
    addingStage.value = true
    try {
        const stage = await api<StageResponse>(
            `/api/pipelines/${selectedPipeline.value.id}/stages`,
            {
                method: 'POST',
                body: {
                    name: newStageName.value.trim(),
                    sortOrder: stages.value.length,
                    code: newStageName.value.toLowerCase().replace(/\s+/g, '-'),
                },
            }
        )
        stages.value.push(stage)
        newStageName.value = ''
        toast.add({ title: 'Stage added', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to add stage', color: 'error' })
    } finally {
        addingStage.value = false
    }
}

async function deleteStage(stageId: string) {
    if (!selectedPipeline.value) return
    try {
        await api(`/api/pipelines/${selectedPipeline.value.id}/stages/${stageId}`, {
            method: 'DELETE',
        })
        stages.value = stages.value.filter((s) => s.id !== stageId)
        toast.add({ title: 'Stage removed', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to remove stage', color: 'error' })
    }
}

// ── Stage reorder ─────────────────────────────────────────────────────────
const draggingStageId = ref<string | null>(null)
const reordering = ref(false)

async function onStageDrop(targetId: string) {
    if (!selectedPipeline.value || !draggingStageId.value || draggingStageId.value === targetId)
        return
    const from = stages.value.findIndex((s) => s.id === draggingStageId.value)
    const to = stages.value.findIndex((s) => s.id === targetId)
    if (from === -1 || to === -1) return
    const reordered = [...stages.value]
    const [moved] = reordered.splice(from, 1)
    reordered.splice(to, 0, moved!)
    stages.value = reordered
    reordering.value = true
    try {
        await api(`/api/pipelines/${selectedPipeline.value.id}/stages/reorder`, {
            method: 'POST',
            body: { stageIds: reordered.map((s) => s.id) },
        })
        toast.add({ title: 'Stages reordered', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to reorder', color: 'error' })
        stages.value = await api<StageResponse[]>(
            `/api/pipelines/${selectedPipeline.value.id}/stages`
        )
    } finally {
        reordering.value = false
        draggingStageId.value = null
    }
}

// ── Create pipeline ────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '' })

function openCreate() {
    Object.assign(createForm, { name: '', description: '' })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/pipelines', {
            method: 'POST',
            body: {
                name: createForm.name,
                description: createForm.description || undefined,
                active: true,
            },
        })
        toast.add({ title: 'Pipeline created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to create pipeline',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creating.value = false
    }
}

// ── Delete pipeline ────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: pipelineToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<PipelineResponse>({
    endpoint: (p) => `/api/pipelines/${p.id}`,
    successMessage: 'Pipeline deleted',
    onDeleted: refresh,
})

const columns: TableColumn<PipelineResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'description', header: 'Description' },
    { id: 'status', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(p: PipelineResponse): DropdownMenuItem[][] {
    return [
        [{ label: 'Stages', icon: 'i-tabler-git-branch', onSelect: () => openStages(p) }],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(p),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Pipelines"
            :subtitle="`${(pipelines?.length ?? 0).toLocaleString()} total`"
        >
            <template #actions>
                <UButton
                    v-if="can('pipelines.create')"
                    icon="i-tabler-plus"
                    label="New Pipeline"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="pipelines ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <span class="font-medium">{{ row.original.name }}</span>
            </template>
            <template #description-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.description ?? '—' }}</span>
            </template>
            <template #status-cell="{ row }">
                <div class="flex gap-1.5">
                    <UBadge
                        v-if="row.original.default"
                        label="Default"
                        color="primary"
                        variant="soft"
                        size="sm"
                    />
                    <UBadge
                        :label="row.original.active ? 'Active' : 'Inactive'"
                        :color="row.original.active ? 'success' : 'neutral'"
                        variant="soft"
                        size="sm"
                    />
                </div>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="can('pipelines.edit')" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-git-merge" message="No pipelines found" />
            </template>
        </AppListTable>

        <!-- Stages management panel (bespoke) -->
        <UModal v-model:open="stagesOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">
                                {{ selectedPipeline?.name }} — Stages
                            </p>
                            <UButton
                                icon="i-tabler-x"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                @click="stagesOpen = false"
                            />
                        </div>
                    </template>
                    <div class="mb-4 flex gap-2">
                        <UInput
                            v-model="newStageName"
                            placeholder="Stage name"
                            class="flex-1"
                            @keydown.enter="addStage"
                        />
                        <UButton
                            icon="i-tabler-plus"
                            label="Add"
                            :loading="addingStage"
                            :disabled="!newStageName.trim()"
                            @click="addStage"
                        />
                    </div>
                    <div v-if="stagesPending" class="space-y-2">
                        <USkeleton v-for="i in 3" :key="i" class="h-9 w-full" />
                    </div>
                    <div
                        v-else-if="stages.length === 0"
                        class="text-muted py-6 text-center text-sm"
                    >
                        No stages yet
                    </div>
                    <div v-else class="space-y-1.5">
                        <div
                            v-for="stage in stages"
                            :key="stage.id"
                            class="border-default flex cursor-grab items-center gap-2 rounded-lg border px-3 py-2 active:cursor-grabbing"
                            draggable="true"
                            @dragstart="draggingStageId = stage.id"
                            @dragend="draggingStageId = null"
                            @dragover.prevent
                            @drop.prevent="onStageDrop(stage.id)"
                        >
                            <UIcon
                                name="i-tabler-grip-vertical"
                                class="text-muted size-4 shrink-0"
                            />
                            <span class="flex-1 text-sm">{{ stage.name }}</span>
                            <UBadge
                                v-if="stage.code"
                                :label="stage.code"
                                color="neutral"
                                variant="soft"
                                size="xs"
                            />
                            <UButton
                                icon="i-tabler-trash"
                                color="error"
                                variant="ghost"
                                size="xs"
                                @click="deleteStage(stage.id)"
                            />
                        </div>
                    </div>
                </UCard>
            </template>
        </UModal>

        <!-- Create pipeline modal -->
        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Pipeline"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.name"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Name" required>
                    <UInput
                        v-model="createForm.name"
                        placeholder="e.g. Sales Pipeline"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="createForm.description"
                        placeholder="Optional description"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Delete pipeline modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Pipeline"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ pipelineToDelete?.name }}</strong
                >? All associated leads will be affected.
            </p>
        </AppConfirmModal>
    </div>
</template>
