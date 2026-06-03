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

// ── All permissions (for create form) ──────────────────────────────────────
const { data: allPermissions } = await useAsyncData<PermissionResponse[]>('all-permissions', () =>
    api<PermissionResponse[]>('/api/roles/permissions')
)

// Group permissions by resource prefix for display
const permissionGroups = computed(() => {
    const groups: Record<string, string[]> = {}
    for (const p of allPermissions.value ?? []) {
        const prefix = p.name.split(':')[0] ?? 'other'
        ;(groups[prefix] ??= []).push(p.name)
    }
    return groups
})

// ── Create ──────────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({
    name: '',
    description: '',
    permissions: [] as string[],
})

function openCreate() {
    Object.assign(createForm, { name: '', description: '', permissions: [] })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/roles', {
            method: 'POST',
            body: {
                name: createForm.name,
                description: createForm.description || undefined,
                permissions: createForm.permissions,
            },
        })
        toast.add({ title: 'Role created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create role', description: e?.data?.message, color: 'error' })
    } finally {
        creating.value = false
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

function rowActions(r: RoleResponse): DropdownMenuItem[][] {
    return [
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
                <AppRowActions v-if="can('roles.delete')" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-shield-off" message="No roles found" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Role"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.name"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Name" required>
                    <UInput v-model="createForm.name" placeholder="e.g. MANAGER" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="createForm.description"
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
                                        createForm.permissions.includes(perm)
                                            ? 'bg-primary/10 border-primary text-primary'
                                            : ''
                                    "
                                >
                                    <input
                                        type="checkbox"
                                        class="hidden"
                                        :checked="createForm.permissions.includes(perm)"
                                        @change="
                                            createForm.permissions.includes(perm)
                                                ? createForm.permissions.splice(
                                                      createForm.permissions.indexOf(perm),
                                                      1
                                                  )
                                                : createForm.permissions.push(perm)
                                        "
                                    />
                                    {{ perm.split(':')[1] }}
                                </label>
                            </div>
                        </div>
                    </div>
                    <p class="text-muted mt-1 text-xs">
                        {{ createForm.permissions.length }} selected
                    </p>
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
