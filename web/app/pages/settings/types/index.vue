<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'

definePageMeta({ title: 'Lead Types' })
useHead({ title: 'Lead Types — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

interface LeadTypeResponse {
    id: string
    name: string
    createdAt: string
}

const {
    data: types,
    pending,
    refresh,
} = await useAsyncData<LeadTypeResponse[]>('lead-types', () =>
    api<LeadTypeResponse[]>('/api/lead-types')
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
        await api('/api/lead-types', { method: 'POST', body: { name: createName.value } })
        toast.add({ title: 'Type created', color: 'success' })
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
const editTarget = shallowRef<LeadTypeResponse | null>(null)
const editName = ref('')

function openEdit(t: LeadTypeResponse) {
    editTarget.value = t
    editName.value = t.name
    editOpen.value = true
}

async function submitEdit() {
    if (!editTarget.value) return
    saving.value = true
    try {
        await api(`/api/lead-types/${editTarget.value.id}`, {
            method: 'PUT',
            body: { name: editName.value },
        })
        toast.add({ title: 'Type updated', color: 'success' })
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
    target: typeToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<LeadTypeResponse>({
    endpoint: (t) => `/api/lead-types/${t.id}`,
    successMessage: 'Type deleted',
    onDeleted: refresh,
})

const columns: TableColumn<LeadTypeResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(t: LeadTypeResponse): DropdownMenuItem[][] {
    return [
        [{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(t) }],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(t),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Lead Types"
            subtitle="Categories for classifying leads (inbound, outbound…)"
        >
            <template #actions>
                <UButton
                    v-if="can('leads.edit')"
                    icon="i-tabler-plus"
                    label="New Type"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="types ?? []" :columns="columns" :loading="pending">
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
                <AppEmptyState icon="i-tabler-category" message="No lead types yet" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="New Lead Type"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createName.trim()"
            @confirm="submitCreate"
        >
            <UFormField label="Name" required>
                <UInput
                    v-model="createName"
                    placeholder="e.g. Inbound, Outbound, Upsell"
                    class="w-full"
                    @keydown.enter="submitCreate"
                />
            </UFormField>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit Lead Type"
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
            title="Delete Type"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ typeToDelete?.name }}</strong
                >? Leads using this type will be unlinked.
            </p>
        </AppConfirmModal>
    </div>
</template>
