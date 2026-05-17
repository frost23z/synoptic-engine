<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { PermissionResponse, RoleResponse } from '~/types/settings'

definePageMeta({ title: 'Roles' })
useHead({ title: 'Roles — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

// ── Roles list ────────────────────────────────────────────────────────────
const {
    data: roles,
    pending,
    refresh,
} = await useAsyncData<RoleResponse[]>('roles', () => api<RoleResponse[]>('/api/roles'))

// ── All permissions (for create/edit form) ────────────────────────────────
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

// ── Create modal ──────────────────────────────────────────────────────────
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

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<RoleResponse | null>(null)
const deleting = ref(false)

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/roles/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Role deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Table columns ─────────────────────────────────────────────────────────
const columns: TableColumn<RoleResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'description', header: 'Description' },
    { id: 'permissions', header: 'Permissions' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(r: RoleResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDelete.value = r
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
                <h2 class="text-highlighted text-xl font-semibold">Roles</h2>
                <p class="text-muted text-sm">{{ (roles?.length ?? 0).toLocaleString() }} total</p>
            </div>
            <UButton icon="i-tabler-plus" label="New Role" @click="openCreate" />
        </div>

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="roles ?? []" :columns="columns" :loading="pending" sticky>
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
                        <UIcon name="i-tabler-shield-off" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No roles found</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <!-- Create role modal -->
        <UModal v-model:open="createOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create Role</p>
                    </template>
                    <form class="space-y-3" @submit.prevent="submitCreate">
                        <UFormField label="Name" required>
                            <UInput
                                v-model="createForm.name"
                                placeholder="e.g. MANAGER"
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
                        <UFormField label="Permissions">
                            <!-- Permission groups for easier selection -->
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
                                :disabled="!createForm.name"
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
                        ><p class="text-highlighted font-semibold">Delete Role</p></template
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
