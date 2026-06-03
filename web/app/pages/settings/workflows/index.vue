<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { WorkflowResponse } from '~/types/settings'

definePageMeta({ title: 'Workflows' })
useHead({ title: 'Workflows — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const KNOWN_EVENTS = [
    'lead.created',
    'lead.updated',
    'lead.stage.changed',
    'person.created',
    'quote.created',
]

const {
    data: workflows,
    pending,
    refresh,
} = await useAsyncData<WorkflowResponse[]>('workflows', () =>
    api<WorkflowResponse[]>('/api/settings/workflows')
)

// ── Create ──────────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({
    name: '',
    description: '',
    eventName: '',
    active: true,
})

function openCreate() {
    Object.assign(createForm, { name: '', description: '', eventName: '', active: true })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/settings/workflows', {
            method: 'POST',
            body: {
                name: createForm.name,
                description: createForm.description || undefined,
                eventName: createForm.eventName,
                conditions: [],
                actions: [],
                isActive: createForm.active,
            },
        })
        toast.add({ title: 'Workflow created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to create workflow',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creating.value = false
    }
}

// ── Delete ──────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: workflowToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<WorkflowResponse>({
    endpoint: (w) => `/api/settings/workflows/${w.id}`,
    successMessage: 'Workflow deleted',
    onDeleted: refresh,
})

const columns: TableColumn<WorkflowResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'eventName', header: 'Trigger Event' },
    { id: 'status', header: 'Status' },
    { id: 'rules', header: 'Rules' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(w: WorkflowResponse): DropdownMenuItem[][] {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(w),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Workflows" subtitle="Automate actions based on CRM events">
            <template #actions>
                <UButton
                    v-if="can('automations.create')"
                    icon="i-tabler-plus"
                    label="New Workflow"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="workflows ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <div>
                    <p class="text-highlighted font-medium">{{ row.original.name }}</p>
                    <p v-if="row.original.description" class="text-muted text-xs">
                        {{ row.original.description }}
                    </p>
                </div>
            </template>
            <template #eventName-cell="{ row }">
                <UBadge :label="row.original.eventName" color="neutral" variant="soft" size="sm" />
            </template>
            <template #status-cell="{ row }">
                <UBadge
                    :label="row.original.active ? 'Active' : 'Inactive'"
                    :color="row.original.active ? 'success' : 'neutral'"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #rules-cell="{ row }">
                <span class="text-muted text-sm">
                    {{ row.original.conditions.length }}c / {{ row.original.actions.length }}a
                </span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="can('automations.delete')" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-git-branch" message="No workflows yet" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Workflow"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.name || !createForm.eventName"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Name" required>
                    <UInput
                        v-model="createForm.name"
                        placeholder="e.g. Notify on new lead"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="createForm.description"
                        placeholder="Optional"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Trigger Event" required>
                    <USelect
                        v-model="createForm.eventName"
                        :items="KNOWN_EVENTS.map((e) => ({ label: e, value: e }))"
                        placeholder="Select event"
                        class="w-full"
                    />
                </UFormField>
                <USwitch v-model="createForm.active" label="Active" />
            </form>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Workflow"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ workflowToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
