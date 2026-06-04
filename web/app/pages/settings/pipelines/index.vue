<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { PipelineResponse } from '~/types/settings'

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

// Pipeline create/delete/stage mutations are guarded by `leads.edit` on the backend
// (only POST /pipelines uses `pipelines.create`).
const canManage = computed(() => can('leads.edit'))

// ── Create pipeline ────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '', rottenDays: 30, isActive: true })

function openCreate() {
    Object.assign(createForm, { name: '', description: '', rottenDays: 30, isActive: true })
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
                isActive: createForm.isActive,
                rottenDays: createForm.rottenDays,
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
    { id: 'stages', header: 'Stages' },
    { id: 'status', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(p: PipelineResponse): DropdownMenuItem[][] {
    return [
        [
            {
                label: 'Manage stages',
                icon: 'i-tabler-git-branch',
                to: `/settings/pipelines/${p.id}`,
            },
        ],
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
                <NuxtLink
                    :to="`/settings/pipelines/${row.original.id}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.name }}
                </NuxtLink>
            </template>
            <template #description-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.description ?? '—' }}</span>
            </template>
            <template #stages-cell="{ row }">
                <UBadge
                    :label="`${row.original.stages?.length ?? 0} stages`"
                    color="neutral"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #status-cell="{ row }">
                <div class="flex gap-1.5">
                    <UBadge
                        v-if="row.original.isDefault"
                        label="Default"
                        color="primary"
                        variant="soft"
                        size="sm"
                    />
                    <UBadge
                        :label="row.original.isActive ? 'Active' : 'Inactive'"
                        :color="row.original.isActive ? 'success' : 'neutral'"
                        variant="soft"
                        size="sm"
                    />
                </div>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="canManage" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-git-merge" message="No pipelines found" />
            </template>
        </AppListTable>

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
                <UFormField
                    label="Rotten days"
                    help="Days of inactivity before a lead is flagged as stale."
                >
                    <UInput v-model.number="createForm.rottenDays" type="number" class="w-full" />
                </UFormField>
                <USwitch v-model="createForm.isActive" label="Active" />
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
