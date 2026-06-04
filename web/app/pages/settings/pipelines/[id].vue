<script setup lang="ts">
import type { PipelineResponse, StageResponse } from '~/types/settings'

definePageMeta({ title: 'Pipeline' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const id = route.params.id as string

// Pipeline + stage mutations are guarded by `leads.edit` on the backend.
const canManage = computed(() => can('leads.edit'))

const {
    data: pipeline,
    pending,
    refresh,
} = await useAsyncData<PipelineResponse>(`pipeline-${id}`, () =>
    api<PipelineResponse>(`/api/pipelines/${id}`)
)

useHead({
    title: computed(() =>
        pipeline.value?.name ? `${pipeline.value.name} — Synoptic` : 'Pipeline — Synoptic'
    ),
})

// ── Stages (local copy, kept sorted) ─────────────────────────────────────────
const stages = ref<StageResponse[]>([])
watch(
    pipeline,
    (p) => {
        stages.value = [...(p?.stages ?? [])].sort((a, b) => a.sortOrder - b.sortOrder)
    },
    { immediate: true }
)

const newStageName = ref('')
const addingStage = ref(false)

async function addStage() {
    const name = newStageName.value.trim()
    if (!name) return
    addingStage.value = true
    try {
        const stage = await api<StageResponse>(`/api/pipelines/${id}/stages`, {
            method: 'POST',
            body: {
                name,
                sortOrder: stages.value.length,
                probability: 0,
                code: name.toLowerCase().replace(/\s+/g, '-'),
            },
        })
        stages.value.push(stage)
        newStageName.value = ''
        toast.add({ title: 'Stage added', color: 'success' })
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to add stage', description: e?.data?.message, color: 'error' })
    } finally {
        addingStage.value = false
    }
}

// ── Edit stage ───────────────────────────────────────────────────────────────
const stageEditOpen = ref(false)
const savingStage = ref(false)
const stageEditTarget = shallowRef<StageResponse | null>(null)
const stageForm = reactive({ name: '', probability: 0, color: '', code: '' })

function openStageEdit(stage: StageResponse) {
    stageEditTarget.value = stage
    Object.assign(stageForm, {
        name: stage.name,
        probability: stage.probability ?? 0,
        color: stage.color ?? '',
        code: stage.code ?? '',
    })
    stageEditOpen.value = true
}

async function submitStageEdit() {
    const target = stageEditTarget.value
    if (!target || !stageForm.name.trim()) return
    savingStage.value = true
    try {
        const updated = await api<StageResponse>(`/api/pipelines/${id}/stages/${target.id}`, {
            method: 'PUT',
            body: {
                name: stageForm.name.trim(),
                sortOrder: target.sortOrder,
                probability: stageForm.probability,
                color: stageForm.color || undefined,
                code: stageForm.code || undefined,
            },
        })
        stages.value = stages.value.map((s) => (s.id === updated.id ? updated : s))
        stageEditOpen.value = false
        toast.add({ title: 'Stage updated', color: 'success' })
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to update stage',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        savingStage.value = false
    }
}

async function deleteStage(stage: StageResponse) {
    try {
        await api(`/api/pipelines/${id}/stages/${stage.id}`, { method: 'DELETE' })
        stages.value = stages.value.filter((s) => s.id !== stage.id)
        toast.add({ title: 'Stage removed', color: 'success' })
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to remove stage',
            description: e?.data?.message,
            color: 'error',
        })
    }
}

// ── Stage reorder (drag/drop) ────────────────────────────────────────────────
const draggingStageId = ref<string | null>(null)
const reordering = ref(false)

async function onStageDrop(targetId: string) {
    if (!draggingStageId.value || draggingStageId.value === targetId) return
    const from = stages.value.findIndex((s) => s.id === draggingStageId.value)
    const to = stages.value.findIndex((s) => s.id === targetId)
    if (from === -1 || to === -1) return
    const reordered = [...stages.value]
    const [moved] = reordered.splice(from, 1)
    reordered.splice(to, 0, moved!)
    stages.value = reordered
    reordering.value = true
    try {
        const updated = await api<PipelineResponse>(`/api/pipelines/${id}/stages/reorder`, {
            method: 'PUT',
            body: { order: reordered.map((s, i) => ({ id: s.id, sortOrder: i })) },
        })
        stages.value = [...updated.stages].sort((a, b) => a.sortOrder - b.sortOrder)
        toast.add({ title: 'Stages reordered', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to reorder', color: 'error' })
        await refresh()
    } finally {
        reordering.value = false
        draggingStageId.value = null
    }
}

// ── Edit pipeline ─────────────────────────────────────────────────────────────
const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
    name: '',
    description: '',
    rottenDays: 30,
    isActive: true,
    isDefault: false,
})

function openEdit() {
    if (!pipeline.value) return
    Object.assign(editForm, {
        name: pipeline.value.name,
        description: pipeline.value.description ?? '',
        rottenDays: pipeline.value.rottenDays,
        isActive: pipeline.value.isActive,
        isDefault: pipeline.value.isDefault,
    })
    editOpen.value = true
}

async function submitEdit() {
    saving.value = true
    try {
        await api(`/api/pipelines/${id}`, {
            method: 'PUT',
            body: {
                name: editForm.name,
                description: editForm.description || undefined,
                isActive: editForm.isActive,
                isDefault: editForm.isDefault,
                rottenDays: editForm.rottenDays,
            },
        })
        toast.add({ title: 'Saved', color: 'success' })
        editOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete pipeline ────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<PipelineResponse>({
    endpoint: (p) => `/api/pipelines/${p.id}`,
    successMessage: 'Pipeline deleted',
    onDeleted: () => {
        router.push('/settings/pipelines')
    },
})
</script>

<template>
    <AppDetailLayout
        v-if="pipeline"
        to="/settings/pipelines"
        :title="pipeline.name"
        :subtitle="pipeline.description || 'Pipeline'"
    >
        <template #actions>
            <UButton
                v-if="canManage"
                icon="i-tabler-pencil"
                label="Edit"
                color="neutral"
                variant="outline"
                @click="openEdit"
            />
            <UButton
                v-if="canManage"
                icon="i-tabler-trash"
                color="error"
                variant="outline"
                @click="promptDelete(pipeline)"
            />
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div class="space-y-6 lg:col-span-2">
                <!-- Stages -->
                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">Stages</p>
                            <UBadge
                                v-if="reordering"
                                label="Saving…"
                                color="neutral"
                                variant="soft"
                                size="xs"
                            />
                        </div>
                    </template>
                    <div v-if="canManage" class="mb-4 flex gap-2">
                        <UInput
                            v-model="newStageName"
                            placeholder="New stage name"
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
                    <div v-if="!stages.length" class="text-muted py-6 text-center text-sm">
                        No stages yet
                    </div>
                    <div v-else class="space-y-1.5">
                        <div
                            v-for="stage in stages"
                            :key="stage.id"
                            class="border-default flex items-center gap-2 rounded-lg border px-3 py-2"
                            :class="canManage ? 'cursor-grab active:cursor-grabbing' : ''"
                            :draggable="canManage"
                            @dragstart="draggingStageId = stage.id"
                            @dragend="draggingStageId = null"
                            @dragover.prevent
                            @drop.prevent="onStageDrop(stage.id)"
                        >
                            <UIcon
                                v-if="canManage"
                                name="i-tabler-grip-vertical"
                                class="text-muted size-4 shrink-0"
                            />
                            <span
                                v-if="stage.color"
                                class="size-3 shrink-0 rounded-full"
                                :style="{ backgroundColor: stage.color }"
                            />
                            <span class="flex-1 text-sm">{{ stage.name }}</span>
                            <UBadge
                                :label="`${stage.probability ?? 0}%`"
                                color="neutral"
                                variant="soft"
                                size="xs"
                            />
                            <UBadge
                                v-if="stage.code"
                                :label="stage.code"
                                color="neutral"
                                variant="soft"
                                size="xs"
                            />
                            <template v-if="canManage">
                                <UButton
                                    icon="i-tabler-pencil"
                                    color="neutral"
                                    variant="ghost"
                                    size="xs"
                                    @click="openStageEdit(stage)"
                                />
                                <UButton
                                    icon="i-tabler-trash"
                                    color="error"
                                    variant="ghost"
                                    size="xs"
                                    @click="deleteStage(stage)"
                                />
                            </template>
                        </div>
                    </div>
                </UCard>
            </div>

            <!-- Details sidebar -->
            <div class="space-y-6">
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Details</p>
                    </template>
                    <dl class="space-y-4 text-sm">
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Status</dt>
                            <dd>
                                <div class="flex gap-1.5">
                                    <UBadge
                                        v-if="pipeline.isDefault"
                                        label="Default"
                                        color="primary"
                                        variant="soft"
                                        size="sm"
                                    />
                                    <UBadge
                                        :label="pipeline.isActive ? 'Active' : 'Inactive'"
                                        :color="pipeline.isActive ? 'success' : 'neutral'"
                                        variant="soft"
                                        size="sm"
                                    />
                                </div>
                            </dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Rotten days</dt>
                            <dd class="text-highlighted">{{ pipeline.rottenDays }}</dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Stages</dt>
                            <dd class="text-highlighted">{{ stages.length }}</dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Created</dt>
                            <dd class="text-muted">{{ formatDate(pipeline.createdAt) }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <!-- Edit pipeline modal -->
        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit Pipeline"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.name.trim()"
            width-class="sm:max-w-2xl"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <UFormField label="Name" required>
                    <UInput v-model="editForm.name" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UTextarea v-model="editForm.description" :rows="2" class="w-full" />
                </UFormField>
                <UFormField label="Rotten days">
                    <UInput v-model.number="editForm.rottenDays" type="number" class="w-full" />
                </UFormField>
                <div class="flex gap-6">
                    <USwitch v-model="editForm.isActive" label="Active" />
                    <USwitch v-model="editForm.isDefault" label="Default" />
                </div>
            </form>
        </AppConfirmModal>

        <!-- Edit stage modal -->
        <AppConfirmModal
            v-model:open="stageEditOpen"
            title="Edit Stage"
            confirm-label="Save"
            :loading="savingStage"
            :confirm-disabled="!stageForm.name.trim()"
            @confirm="submitStageEdit"
        >
            <form class="space-y-3" @submit.prevent="submitStageEdit">
                <UFormField label="Name" required>
                    <UInput v-model="stageForm.name" class="w-full" autofocus />
                </UFormField>
                <UFormField label="Win probability (%)">
                    <UInput
                        v-model.number="stageForm.probability"
                        type="number"
                        :min="0"
                        :max="100"
                        class="w-full"
                    />
                </UFormField>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Color">
                        <UInput v-model="stageForm.color" placeholder="#22c55e" class="w-full" />
                    </UFormField>
                    <UFormField label="Code">
                        <UInput v-model="stageForm.code" placeholder="won" class="w-full" />
                    </UFormField>
                </div>
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
                Delete <strong class="text-highlighted">{{ pipeline.name }}</strong
                >? All associated leads will be affected.
            </p>
        </AppConfirmModal>
    </AppDetailLayout>

    <div v-else-if="pending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <div class="grid grid-cols-3 gap-6">
            <USkeleton class="col-span-2 h-40 w-full" />
            <USkeleton class="h-40 w-full" />
        </div>
    </div>
</template>
