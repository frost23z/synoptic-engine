<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'

definePageMeta({ title: 'Lead Sources' })
useHead({ title: 'Lead Sources — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

interface LeadSourceResponse {
    id: string
    name: string
    createdAt: string
}

const {
    data: sources,
    pending,
    refresh,
} = await useAsyncData<LeadSourceResponse[]>('lead-sources', () =>
    api<LeadSourceResponse[]>('/api/lead-sources')
)

// ── Create ────────────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createName = ref('')

function openCreate() {
    createName.value = ''
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/lead-sources', { method: 'POST', body: { name: createName.value } })
        toast.add({ title: 'Source created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create', description: e?.data?.message, color: 'error' })
    } finally {
        creating.value = false
    }
}

// ── Edit ──────────────────────────────────────────────────────────────────
const editOpen = ref(false)
const saving = ref(false)
const editTarget = shallowRef<LeadSourceResponse | null>(null)
const editName = ref('')

function openEdit(s: LeadSourceResponse) {
    editTarget.value = s
    editName.value = s.name
    editOpen.value = true
}

async function submitEdit() {
    if (!editTarget.value) return
    saving.value = true
    try {
        await api(`/api/lead-sources/${editTarget.value.id}`, {
            method: 'PUT',
            body: { name: editName.value },
        })
        toast.add({ title: 'Source updated', color: 'success' })
        editOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to update', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: sourceToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<LeadSourceResponse>({
    endpoint: (s) => `/api/lead-sources/${s.id}`,
    successMessage: 'Source deleted',
    onDeleted: refresh,
})

const columns: TableColumn<LeadSourceResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(s: LeadSourceResponse): DropdownMenuItem[][] {
    return [
        [{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(s) }],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(s),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Lead Sources"
            subtitle="Where leads originate (website, referral, cold call…)"
        >
            <template #actions>
                <UButton
                    v-if="can('leads.edit')"
                    icon="i-tabler-plus"
                    label="New Source"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="sources ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <span class="font-medium">{{ row.original.name }}</span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="can('leads.edit')" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-source-code" message="No lead sources yet" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="New Lead Source"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createName.trim()"
            @confirm="submitCreate"
        >
            <UFormField label="Name" required>
                <UInput
                    v-model="createName"
                    placeholder="e.g. Website, Cold Call, Referral"
                    class="w-full"
                    @keydown.enter="submitCreate"
                />
            </UFormField>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit Lead Source"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editName.trim()"
            @confirm="submitEdit"
        >
            <UFormField label="Name" required>
                <UInput v-model="editName" class="w-full" @keydown.enter="submitEdit" />
            </UFormField>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Source"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ sourceToDelete?.name }}</strong
                >? Leads using this source will be unlinked.
            </p>
        </AppConfirmModal>
    </div>
</template>
