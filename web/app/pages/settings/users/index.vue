<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { RoleResponse, UserResponse } from '~/types/settings'

definePageMeta({ title: 'Users' })
useHead({ title: 'Users — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const {
    page,
    search,
    items: users,
    total,
    pending,
    refresh,
} = await usePaginatedList<UserResponse>('/api/users', { key: 'users' })

// ── Available roles for create form ─────────────────────────────────────
const { data: availableRoles } = await useAsyncData<RoleResponse[]>('roles-list', () =>
    api<RoleResponse[]>('/api/roles')
)
const roleOptions = computed(
    () => availableRoles.value?.map((r) => ({ label: r.name, value: r.name })) ?? []
)

// ── Create ────────────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    roles: ['SALESPERSON'] as string[],
})

function openCreate() {
    Object.assign(createForm, {
        email: '',
        password: '',
        firstName: '',
        lastName: '',
        roles: ['SALESPERSON'],
    })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/users', {
            method: 'POST',
            body: {
                email: createForm.email,
                password: createForm.password,
                firstName: createForm.firstName,
                lastName: createForm.lastName,
                roles: createForm.roles,
            },
        })
        toast.add({ title: 'User created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create user', description: e?.data?.message, color: 'error' })
    } finally {
        creating.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: userToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<UserResponse>({
    endpoint: (u) => `/api/users/${u.id}`,
    successMessage: 'User deleted',
    onDeleted: refresh,
})

const columns: TableColumn<UserResponse>[] = [
    { accessorKey: 'fullName', header: 'Name' },
    { accessorKey: 'email', header: 'Email' },
    { accessorKey: 'active', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(u: UserResponse): DropdownMenuItem[][] {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(u),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Users" :subtitle="`${total.toLocaleString()} total`">
            <template #actions>
                <UButton
                    v-if="can('users.create')"
                    icon="i-tabler-plus"
                    label="New User"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <UInput v-model="search" placeholder="Search users…" icon="i-tabler-search" class="w-64" />

        <AppListTable :rows="users" :columns="columns" :loading="pending">
            <template #fullName-cell="{ row }">
                <p class="text-highlighted font-medium">{{ row.original.fullName }}</p>
            </template>
            <template #email-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.email }}</span>
            </template>
            <template #active-cell="{ row }">
                <UBadge
                    :label="row.original.active ? 'Active' : 'Inactive'"
                    :color="row.original.active ? 'success' : 'neutral'"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="can('users.delete')" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-user-off" message="No users found" />
            </template>
        </AppListTable>

        <AppPagination v-model:page="page" :total="total" />

        <AppConfirmModal
            v-model:open="createOpen"
            title="Create User"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="
                !createForm.email ||
                !createForm.password ||
                !createForm.firstName ||
                !createForm.lastName
            "
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="First name" required>
                        <UInput v-model="createForm.firstName" placeholder="John" class="w-full" />
                    </UFormField>
                    <UFormField label="Last name" required>
                        <UInput v-model="createForm.lastName" placeholder="Doe" class="w-full" />
                    </UFormField>
                </div>
                <UFormField label="Email" required>
                    <UInput
                        v-model="createForm.email"
                        type="email"
                        placeholder="john@company.com"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Password" required>
                    <UInput
                        v-model="createForm.password"
                        type="password"
                        placeholder="Min 8 characters"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Roles">
                    <USelect
                        v-model="createForm.roles"
                        :items="roleOptions"
                        multiple
                        placeholder="Select roles"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete User"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ userToDelete?.fullName }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
