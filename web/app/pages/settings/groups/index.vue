<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { GroupResponse } from '~/types/settings'

definePageMeta({ title: 'Groups' })
useHead({ title: 'Groups — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const {
    data: groups,
    pending,
    refresh,
} = await useAsyncData<GroupResponse[]>('groups', () => api<GroupResponse[]>('/api/groups'))

// ── Create ──────────────────────────────────────────────────────────────
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
        await api('/api/groups', {
            method: 'POST',
            body: { name: createForm.name, description: createForm.description || undefined },
        })
        toast.add({ title: 'Group created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to create group',
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
    target: groupToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<GroupResponse>({
    endpoint: (g) => `/api/groups/${g.id}`,
    successMessage: 'Group deleted',
    onDeleted: refresh,
})

const columns: TableColumn<GroupResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'description', header: 'Description' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(g: GroupResponse): DropdownMenuItem[][] {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(g),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Groups" :subtitle="`${(groups?.length ?? 0).toLocaleString()} total`">
            <template #actions>
                <UButton
                    v-if="can('groups.create')"
                    icon="i-tabler-plus"
                    label="New Group"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="groups ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <span class="font-medium">{{ row.original.name }}</span>
            </template>
            <template #description-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.description ?? '—' }}</span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="can('groups.delete')" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-users-group" message="No groups found" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Group"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.name"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Name" required>
                    <UInput
                        v-model="createForm.name"
                        placeholder="e.g. Sales Team"
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

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Group"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ groupToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
