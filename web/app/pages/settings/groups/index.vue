<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import { zCreateGroupRequest } from '~/api/zod.gen'
import type { GroupResponse } from '~/types/settings'

definePageMeta({ title: 'Groups' })
useHead({ title: 'Groups — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()
const {
    submitting: saving,
    errors,
    validate,
    run,
    clearErrors,
} = useFormSubmit({ failureTitle: 'Failed to save group' })

const {
    data: groups,
    pending,
    refresh,
} = await useAsyncData<GroupResponse[]>('groups', () => api<GroupResponse[]>('/api/groups'))

// ── Create / Edit (shared modal) ────────────────────────────────────────────
const formOpen = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({ name: '', description: '' })

const isEdit = computed(() => editingId.value !== null)

function openCreate() {
    clearErrors()
    editingId.value = null
    Object.assign(form, { name: '', description: '' })
    formOpen.value = true
}

function openEdit(g: GroupResponse) {
    clearErrors()
    editingId.value = g.id
    Object.assign(form, { name: g.name, description: g.description ?? '' })
    formOpen.value = true
}

function submitForm() {
    run({
        validate: () => validate(form, zCreateGroupRequest),
        call: () =>
            api(isEdit.value ? `/api/groups/${editingId.value}` : '/api/groups', {
                method: isEdit.value ? 'PUT' : 'POST',
                body: { name: form.name, description: form.description || undefined },
            }),
        fieldHints: ['name'],
        onSuccess: () => {
            toast.add({ title: isEdit.value ? 'Group updated' : 'Group created', color: 'success' })
            formOpen.value = false
            refresh()
        },
    })
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

const canEdit = computed(() => can('groups.edit'))
const canDelete = computed(() => can('groups.delete'))

function rowActions(g: GroupResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = []
    if (canEdit.value) {
        items.push([{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(g) }])
    }
    if (canDelete.value) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(g),
            },
        ])
    }
    return items
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
                <AppRowActions v-if="canEdit || canDelete" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-users-group" message="No groups found" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="formOpen"
            :title="isEdit ? 'Edit Group' : 'Create Group'"
            :confirm-label="isEdit ? 'Save' : 'Create'"
            :loading="saving"
            :confirm-disabled="!form.name"
            @confirm="submitForm"
        >
            <form class="space-y-3" @submit.prevent="submitForm">
                <UFormField label="Name" required :error="errors.name">
                    <UInput v-model="form.name" placeholder="e.g. Sales Team" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="form.description"
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
