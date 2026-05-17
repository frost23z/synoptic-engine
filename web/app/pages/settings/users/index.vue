<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { RoleResponse, UserResponse, UsersPage } from '~/types/settings'

definePageMeta({ title: 'Users' })
useHead({ title: 'Users — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

const page = ref(1)
const PAGE_SIZE = 20
const search = ref('')
const debouncedSearch = refDebounced(search, 300)

const queryKey = computed(() => ['users', page.value, debouncedSearch.value])

const {
    data: usersPage,
    pending,
    refresh,
} = await useAsyncData<UsersPage>(
    () => queryKey.value.join('-'),
    () => {
        const params: Record<string, string | number> = { page: page.value - 1, size: PAGE_SIZE }
        if (debouncedSearch.value) params.q = debouncedSearch.value
        return api<UsersPage>('/api/users', { params })
    },
    { watch: [queryKey] }
)

const users = computed(() => usersPage.value?.content ?? [])
const total = computed(() => usersPage.value?.totalElements ?? 0)

// ── Available roles for create form ─────────────────────────────────────
const { data: availableRoles } = await useAsyncData<RoleResponse[]>('roles-list', () =>
    api<RoleResponse[]>('/api/roles')
)
const roleOptions = computed(
    () => availableRoles.value?.map((r) => ({ label: r.name, value: r.name })) ?? []
)

// ── Create modal ─────────────────────────────────────────────────────────
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
        toast.add({
            title: 'Failed to create user',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creating.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<UserResponse | null>(null)
const deleting = ref(false)

function openDeleteModal(u: UserResponse) {
    toDelete.value = u
    deleteOpen.value = true
}

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/users/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'User deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Table columns ─────────────────────────────────────────────────────────
const columns: TableColumn<UserResponse>[] = [
    { accessorKey: 'fullName', header: 'Name' },
    { accessorKey: 'email', header: 'Email' },
    { accessorKey: 'active', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(u: UserResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => openDeleteModal(u),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Users</h2>
                <p class="text-muted text-sm">{{ total.toLocaleString() }} total</p>
            </div>
            <UButton icon="i-tabler-plus" label="New User" @click="openCreate" />
        </div>

        <UInput v-model="search" placeholder="Search users…" icon="i-tabler-search" class="w-64" />

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="users" :columns="columns" :loading="pending" sticky>
                <template #fullName-cell="{ row }">
                    <div>
                        <p class="text-highlighted font-medium">{{ row.original.fullName }}</p>
                    </div>
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
                        <UIcon name="i-tabler-user-off" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No users found</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <div v-if="total > PAGE_SIZE" class="flex justify-center">
            <UPagination
                v-model:page="page"
                :total="total"
                :items-per-page="PAGE_SIZE"
                :sibling-count="1"
                show-edges
            />
        </div>

        <!-- Create user modal -->
        <UModal v-model:open="createOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create User</p>
                    </template>
                    <form class="space-y-3" @submit.prevent="submitCreate">
                        <div class="grid grid-cols-2 gap-3">
                            <UFormField label="First name" required>
                                <UInput
                                    v-model="createForm.firstName"
                                    placeholder="John"
                                    class="w-full"
                                />
                            </UFormField>
                            <UFormField label="Last name" required>
                                <UInput
                                    v-model="createForm.lastName"
                                    placeholder="Doe"
                                    class="w-full"
                                />
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
                                :disabled="
                                    !createForm.email ||
                                    !createForm.password ||
                                    !createForm.firstName ||
                                    !createForm.lastName
                                "
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
                        ><p class="text-highlighted font-semibold">Delete User</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ toDelete?.fullName }}</strong
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
