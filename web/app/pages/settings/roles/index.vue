<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { PermissionResponse, RoleResponse } from '~/types/settings'

definePageMeta({ title: 'Roles' })
useHead({ title: 'Roles — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

// ── Roles list ────────────────────────────────────────────────────────────
const {
    data: roles,
    pending,
    refresh,
} = await useAsyncData<RoleResponse[]>('roles', () => api<RoleResponse[]>('/api/roles'))

// ── All permissions (for create/edit form) ──────────────────────────────────
const { data: allPermissions } = await useAsyncData<PermissionResponse[]>('all-permissions', () =>
    api<PermissionResponse[]>('/api/roles/permissions')
)

// Group permissions by their top-level segment (keys are dot-delimited, e.g. `leads.view`).
const permissionGroups = computed(() => {
    const groups: Record<string, string[]> = {}
    for (const p of allPermissions.value ?? []) {
        const prefix = p.name.split('.')[0] ?? 'other'
        ;(groups[prefix] ??= []).push(p.name)
    }
    return groups
})

function actionLabel(perm: string) {
    return perm.split('.').slice(1).join('.') || perm
}

// ── Create / Edit (shared modal) ────────────────────────────────────────────
const formOpen = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({
    name: '',
    description: '',
    permissions: [] as string[],
})

const isEdit = computed(() => editingId.value !== null)

function togglePermission(perm: string) {
    const i = form.permissions.indexOf(perm)
    if (i === -1) form.permissions.push(perm)
    else form.permissions.splice(i, 1)
}

function openCreate() {
    editingId.value = null
    Object.assign(form, { name: '', description: '', permissions: [] })
    formOpen.value = true
}

function openEdit(r: RoleResponse) {
    editingId.value = r.id
    Object.assign(form, {
        name: r.name,
        description: r.description ?? '',
        permissions: [...r.permissions],
    })
    formOpen.value = true
}

async function submitForm() {
    saving.value = true
    try {
        await api(isEdit.value ? `/api/roles/${editingId.value}` : '/api/roles', {
            method: isEdit.value ? 'PUT' : 'POST',
            body: {
                name: form.name,
                description: form.description || undefined,
                permissions: form.permissions,
            },
        })
        toast.add({ title: isEdit.value ? 'Role updated' : 'Role created', color: 'success' })
        formOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: isEdit.value ? 'Failed to update role' : 'Failed to create role',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        saving.value = false
    }
}

// ── Delete ──────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: roleToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<RoleResponse>({
    endpoint: (r) => `/api/roles/${r.id}`,
    successMessage: 'Role deleted',
    onDeleted: refresh,
})

const columns: TableColumn<RoleResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'description', header: 'Description' },
    { id: 'permissions', header: 'Permissions' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

// Backend gates role update AND delete on `roles.edit` (there is no `roles.delete` key).
const canManage = computed(() => can('roles.edit'))

function rowActions(r: RoleResponse): DropdownMenuItem[][] {
    return [
        [{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(r) }],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(r),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Roles" :subtitle="`${(roles?.length ?? 0).toLocaleString()} total`">
            <template #actions>
                <UButton
                    v-if="can('roles.create')"
                    icon="i-tabler-plus"
                    label="New Role"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="roles ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <span class="font-medium">{{ row.original.name }}</span>
            </template>
            <template #description-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.description ?? '—' }}</span>
            </template>
            <template #permissions-cell="{ row }">
                <UBadge
                    :label="`${row.original.permissions.length} permissions`"
                    color="neutral"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="canManage" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-shield-off" message="No roles found" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="formOpen"
            :title="isEdit ? 'Edit Role' : 'Create Role'"
            :confirm-label="isEdit ? 'Save' : 'Create'"
            :loading="saving"
            :confirm-disabled="!form.name"
            width-class="sm:max-w-2xl"
            @confirm="submitForm"
        >
            <form class="space-y-3" @submit.prevent="submitForm">
                <UFormField label="Name" required>
                    <UInput v-model="form.name" placeholder="e.g. MANAGER" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="form.description"
                        placeholder="Optional description"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Permissions">
                    <div
                        class="border-default max-h-56 space-y-3 overflow-y-auto rounded-md border p-3"
                    >
                        <div v-for="(perms, group) in permissionGroups" :key="group">
                            <p class="text-muted mb-1.5 text-xs font-semibold uppercase">
                                {{ group }}
                            </p>
                            <div class="flex flex-wrap gap-1.5">
                                <label
                                    v-for="perm in perms"
                                    :key="perm"
                                    class="border-default hover:bg-muted flex cursor-pointer items-center gap-1.5 rounded-md border px-2 py-1 text-xs transition-colors"
                                    :class="
                                        form.permissions.includes(perm)
                                            ? 'bg-primary/10 border-primary text-primary'
                                            : ''
                                    "
                                >
                                    <input
                                        type="checkbox"
                                        class="hidden"
                                        :checked="form.permissions.includes(perm)"
                                        @change="togglePermission(perm)"
                                    />
                                    {{ actionLabel(perm) }}
                                </label>
                            </div>
                        </div>
                    </div>
                    <p class="text-muted mt-1 text-xs">{{ form.permissions.length }} selected</p>
                </UFormField>
            </form>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Role"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ roleToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
