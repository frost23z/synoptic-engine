<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { WorkflowResponse } from '~/types/settings'

definePageMeta({ title: 'Workflows' })
useHead({ title: 'Workflows — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

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

// ── Create modal ──────────────────────────────────────────────────────────
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

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<WorkflowResponse | null>(null)
const deleting = ref(false)

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/settings/workflows/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Workflow deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Table columns ─────────────────────────────────────────────────────────
const columns: TableColumn<WorkflowResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'eventName', header: 'Trigger Event' },
    { id: 'status', header: 'Status' },
    { id: 'rules', header: 'Rules' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(w: WorkflowResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDelete.value = w
                    deleteOpen.value = true
                },
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Workflows</h2>
                <p class="text-muted text-sm">Automate actions based on CRM events</p>
            </div>
            <UButton icon="i-tabler-plus" label="New Workflow" @click="openCreate" />
        </div>

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="workflows ?? []" :columns="columns" :loading="pending" sticky>
                <template #name-cell="{ row }">
                    <div>
                        <p class="text-highlighted font-medium">{{ row.original.name }}</p>
                        <p v-if="row.original.description" class="text-muted text-xs">
                            {{ row.original.description }}
                        </p>
                    </div>
                </template>
                <template #eventName-cell="{ row }">
                    <UBadge
                        :label="row.original.eventName"
                        color="neutral"
                        variant="soft"
                        size="sm"
                    />
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
                    <UDropdownMenu :items="rowActions(row.original)">
                        <UButton
                            icon="i-tabler-dots-vertical"
                            color="neutral"
                            variant="ghost"
                            size="xs"
                        />
                    </UDropdownMenu>
                </template>
                <template #empty>
                    <div class="space-y-2 py-12 text-center">
                        <UIcon name="i-tabler-git-branch" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No workflows yet</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <!-- Create modal -->
        <UModal v-model:open="createOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create Workflow</p>
                    </template>
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
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="createOpen = false"
                            />
                            <UButton
                                label="Create"
                                :loading="creating"
                                :disabled="!createForm.name || !createForm.eventName"
                                @click="submitCreate"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete modal -->
        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Workflow</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ toDelete?.name }}</strong
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
