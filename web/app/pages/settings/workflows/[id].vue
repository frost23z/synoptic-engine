<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { WorkflowActionRunResponse, WorkflowResponse } from '~/types/settings'

definePageMeta({ title: 'Workflow' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const id = route.params.id as string

const canManage = computed(() => can('automations.edit'))

const {
    data: workflow,
    pending,
    refresh,
} = await useAsyncData<WorkflowResponse>(`workflow-${id}`, () =>
    api<WorkflowResponse>(`/api/settings/workflows/${id}`)
)

useHead({
    title: computed(() =>
        workflow.value?.name ? `${workflow.value.name} — Synoptic` : 'Workflow — Synoptic'
    ),
})

// ── Run history ───────────────────────────────────────────────────────────
const {
    page,
    items: runs,
    total: totalRuns,
    pending: runsPending,
} = await usePaginatedList<WorkflowActionRunResponse>(`/api/settings/workflows/${id}/runs`, {
    key: `workflow-${id}-runs`,
})

const RUN_STATUS_COLOR: Record<string, 'success' | 'error' | 'neutral'> = {
    SUCCESS: 'success',
    FAILED: 'error',
    SKIPPED: 'neutral',
}
const runStatusColor = (s: string) => RUN_STATUS_COLOR[s] ?? 'neutral'

const runColumns: TableColumn<WorkflowActionRunResponse>[] = [
    { accessorKey: 'createdAt', header: 'When' },
    { accessorKey: 'actionType', header: 'Action' },
    { id: 'entity', header: 'Entity' },
    { accessorKey: 'status', header: 'Status' },
    { accessorKey: 'errorMessage', header: 'Detail' },
]

const prettyConditions = computed(() => JSON.stringify(workflow.value?.conditions ?? [], null, 2))
const prettyActions = computed(() => JSON.stringify(workflow.value?.actions ?? [], null, 2))

// ── Edit ─────────────────────────────────────────────────────────────────────
const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
    name: '',
    description: '',
    eventName: '',
    conditionType: 'and',
    isActive: true,
})

const CONDITION_TYPES = [
    { label: 'AND — all conditions match', value: 'and' },
    { label: 'OR — any condition matches', value: 'or' },
]

function openEdit() {
    if (!workflow.value) return
    Object.assign(editForm, {
        name: workflow.value.name,
        description: workflow.value.description ?? '',
        eventName: workflow.value.eventName,
        conditionType: workflow.value.conditionType || 'and',
        isActive: workflow.value.isActive,
    })
    editOpen.value = true
}

async function submitEdit() {
    if (!workflow.value) return
    saving.value = true
    try {
        await api(`/api/settings/workflows/${id}`, {
            method: 'PUT',
            body: {
                name: editForm.name,
                description: editForm.description || undefined,
                eventName: editForm.eventName,
                conditions: workflow.value.conditions,
                actions: workflow.value.actions,
                conditionType: editForm.conditionType,
                isActive: editForm.isActive,
            },
        })
        toast.add({ title: 'Workflow saved', color: 'success' })
        editOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ─────────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<WorkflowResponse>({
    endpoint: (w) => `/api/settings/workflows/${w.id}`,
    successMessage: 'Workflow deleted',
    onDeleted: () => {
        router.push('/settings/workflows')
    },
})
</script>

<template>
    <AppDetailLayout
        v-if="workflow"
        to="/settings/workflows"
        :title="workflow.name"
        :subtitle="workflow.description || 'Workflow'"
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
                @click="promptDelete(workflow)"
            />
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div class="space-y-6 lg:col-span-2">
                <!-- Run history -->
                <UCard :ui="{ body: 'p-0' }">
                    <template #header>
                        <p class="text-highlighted font-semibold">
                            Run history
                            <span class="text-muted font-normal">({{ totalRuns }})</span>
                        </p>
                    </template>
                    <UTable :data="runs" :columns="runColumns" :loading="runsPending">
                        <template #createdAt-cell="{ row }">
                            <span class="text-muted text-sm">{{
                                formatDate(row.original.createdAt)
                            }}</span>
                        </template>
                        <template #actionType-cell="{ row }">
                            <span class="font-medium">{{ row.original.actionType }}</span>
                        </template>
                        <template #entity-cell="{ row }">
                            <span class="text-muted text-sm">
                                {{ row.original.entityType }} ·
                                {{ row.original.entityId.slice(0, 8) }}
                            </span>
                        </template>
                        <template #status-cell="{ row }">
                            <UBadge
                                :label="row.original.status"
                                :color="runStatusColor(row.original.status)"
                                variant="soft"
                                size="sm"
                            />
                        </template>
                        <template #errorMessage-cell="{ row }">
                            <span class="text-error text-xs">{{
                                row.original.errorMessage ?? '—'
                            }}</span>
                        </template>
                        <template #empty>
                            <AppEmptyState icon="i-tabler-history-off" message="No runs yet" />
                        </template>
                    </UTable>
                    <div class="p-3">
                        <AppPagination v-model:page="page" :total="totalRuns" />
                    </div>
                </UCard>

                <!-- Rules -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Rules</p>
                    </template>
                    <div class="space-y-4">
                        <div>
                            <p class="text-muted mb-1 text-xs font-semibold uppercase">
                                Conditions ({{ workflow.conditionType }})
                            </p>
                            <pre
                                class="bg-muted/40 overflow-x-auto rounded-lg p-3 font-mono text-xs"
                                >{{ prettyConditions }}</pre
                            >
                        </div>
                        <div>
                            <p class="text-muted mb-1 text-xs font-semibold uppercase">Actions</p>
                            <pre
                                class="bg-muted/40 overflow-x-auto rounded-lg p-3 font-mono text-xs"
                                >{{ prettyActions }}</pre
                            >
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
                        <div class="flex items-center justify-between gap-2">
                            <dt class="text-muted">Status</dt>
                            <dd>
                                <UBadge
                                    :label="workflow.isActive ? 'Active' : 'Inactive'"
                                    :color="workflow.isActive ? 'success' : 'neutral'"
                                    variant="soft"
                                    size="sm"
                                />
                            </dd>
                        </div>
                        <div class="flex items-center justify-between gap-2">
                            <dt class="text-muted">Trigger event</dt>
                            <dd>
                                <UBadge
                                    :label="workflow.eventName"
                                    color="neutral"
                                    variant="soft"
                                    size="sm"
                                />
                            </dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Match</dt>
                            <dd class="text-highlighted uppercase">{{ workflow.conditionType }}</dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Created</dt>
                            <dd class="text-muted">{{ formatDate(workflow.createdAt) }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit Workflow"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.name.trim() || !editForm.eventName.trim()"
            width-class="sm:max-w-2xl"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <UFormField label="Name" required>
                    <UInput v-model="editForm.name" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UInput v-model="editForm.description" class="w-full" />
                </UFormField>
                <UFormField label="Trigger event" required>
                    <UInput
                        v-model="editForm.eventName"
                        placeholder="lead.created"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Condition match">
                    <USelect
                        v-model="editForm.conditionType"
                        :items="CONDITION_TYPES"
                        class="w-full"
                    />
                </UFormField>
                <USwitch v-model="editForm.isActive" label="Active" />
            </form>
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Workflow"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ workflow.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </AppDetailLayout>

    <div v-else-if="pending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <div class="grid grid-cols-3 gap-6">
            <USkeleton class="col-span-2 h-64 w-full" />
            <USkeleton class="h-40 w-full" />
        </div>
    </div>
</template>
