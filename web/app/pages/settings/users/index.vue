<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type {
    GroupResponse,
    RoleResponse,
    UserDetailResponse,
    UserResponse,
    ViewPermission,
} from '~/types/settings'
import { email, minLength, required } from '~/utils/validators'

definePageMeta({ title: 'Users' })

const PASSWORD_MIN = minLength(8, 'Password must be at least 8 characters')
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

// ── Lookups for create/edit forms ───────────────────────────────────────
const { data: availableRoles } = await useAsyncData<RoleResponse[]>('roles-list', () =>
    api<RoleResponse[]>('/api/roles')
)
const roleOptions = computed(
    () => availableRoles.value?.map((r) => ({ label: r.name, value: r.name })) ?? []
)

const { data: availableGroups } = await useAsyncData<GroupResponse[]>('groups-list', () =>
    can('groups.view') ? api<GroupResponse[]>('/api/groups') : Promise.resolve([])
)
const groupOptions = computed(
    () => availableGroups.value?.map((g) => ({ label: g.name, value: g.id })) ?? []
)

const VIEW_PERMISSIONS: { label: string; value: ViewPermission }[] = [
    { label: 'All records', value: 'ALL' },
    { label: 'Global', value: 'GLOBAL' },
    { label: 'Group', value: 'GROUP' },
    { label: 'Individual', value: 'INDIVIDUAL' },
]

// ── Create ────────────────────────────────────────────────────────────────
const createOpen = ref(false)
const {
    submitting: creating,
    errors: createErrors,
    validate: validateCreate,
    run: runCreate,
    clearErrors: clearCreate,
} = useFormSubmit({ failureTitle: 'Failed to create user' })
const createForm = reactive({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    roles: ['SALESPERSON'] as string[],
})

function openCreate() {
    clearCreate()
    Object.assign(createForm, {
        email: '',
        password: '',
        firstName: '',
        lastName: '',
        roles: ['SALESPERSON'],
    })
    createOpen.value = true
}

function submitCreate() {
    runCreate({
        validate: () =>
            validateCreate(createForm, {
                firstName: [required('First name is required')],
                lastName: [required('Last name is required')],
                email: [required('Email is required'), email()],
                password: [required('Password is required'), PASSWORD_MIN],
            }),
        call: () =>
            api('/api/users', {
                method: 'POST',
                body: {
                    email: createForm.email,
                    password: createForm.password,
                    firstName: createForm.firstName,
                    lastName: createForm.lastName,
                    roles: createForm.roles,
                },
            }),
        fieldHints: ['email', 'password'],
        onSuccess: () => {
            toast.add({ title: 'User created', color: 'success' })
            createOpen.value = false
            refresh()
        },
    })
}

// ── Edit ────────────────────────────────────────────────────────────────
const editOpen = ref(false)
const loadingDetail = ref(false)
const editId = ref<string | null>(null)
const {
    submitting: saving,
    errors: editErrors,
    validate: validateEdit,
    run: runEdit,
    clearErrors: clearEdit,
} = useFormSubmit({ failureTitle: 'Failed to update user' })
const editForm = reactive({
    firstName: '',
    lastName: '',
    phone: '',
    roles: [] as string[],
    groups: [] as string[],
    viewPermission: 'GLOBAL' as ViewPermission,
})

async function openEdit(u: UserResponse) {
    clearEdit()
    editId.value = u.id
    editOpen.value = true
    loadingDetail.value = true
    try {
        const detail = await api<UserDetailResponse>(`/api/users/${u.id}`)
        Object.assign(editForm, {
            firstName: detail.firstName,
            lastName: detail.lastName,
            phone: detail.phone ?? '',
            roles: detail.roles,
            groups: detail.groups.map((g) => g.id),
            viewPermission: detail.viewPermission,
        })
    } catch {
        toast.add({ title: 'Failed to load user', color: 'error' })
        editOpen.value = false
    } finally {
        loadingDetail.value = false
    }
}

function submitEdit() {
    const uid = editId.value
    if (!uid) return
    runEdit({
        validate: () =>
            validateEdit(editForm, {
                firstName: [required('First name is required')],
                lastName: [required('Last name is required')],
            }),
        call: () =>
            api(`/api/users/${uid}`, {
                method: 'PUT',
                body: {
                    firstName: editForm.firstName,
                    lastName: editForm.lastName,
                    phone: editForm.phone || undefined,
                    roles: editForm.roles,
                    groups: editForm.groups,
                    viewPermission: editForm.viewPermission,
                },
            }),
        onSuccess: () => {
            toast.add({ title: 'User updated', color: 'success' })
            editOpen.value = false
            refresh()
        },
    })
}

// ── Set password ──────────────────────────────────────────────────────────
const passwordOpen = ref(false)
const passwordId = ref<string | null>(null)
const passwordName = ref('')
const newPassword = ref('')
const {
    submitting: savingPassword,
    errors: pwErrors,
    validate: validatePw,
    run: runPw,
    clearErrors: clearPw,
} = useFormSubmit({ failureTitle: 'Failed to set password' })

function openPassword(u: UserResponse) {
    clearPw()
    passwordId.value = u.id
    passwordName.value = u.fullName
    newPassword.value = ''
    passwordOpen.value = true
}

function submitPassword() {
    const uid = passwordId.value
    if (!uid) return
    runPw({
        validate: () =>
            validatePw(
                { password: newPassword.value },
                { password: [required('Password is required'), PASSWORD_MIN] }
            ),
        call: () =>
            api(`/api/users/${uid}/password`, {
                method: 'PUT',
                body: { password: newPassword.value },
            }),
        fieldHints: ['password'],
        onSuccess: () => {
            toast.add({ title: 'Password updated', color: 'success' })
            passwordOpen.value = false
        },
    })
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
    successMessage: 'User deactivated',
    onDeleted: refresh,
})

const columns: TableColumn<UserResponse>[] = [
    { accessorKey: 'fullName', header: 'Name' },
    { accessorKey: 'email', header: 'Email' },
    { accessorKey: 'isActive', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

const canEdit = computed(() => can('users.edit'))
const canDelete = computed(() => can('users.delete'))

function rowActions(u: UserResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = []
    if (canEdit.value) {
        items.push([
            { label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(u) },
            { label: 'Set password', icon: 'i-tabler-key', onSelect: () => openPassword(u) },
        ])
    }
    if (canDelete.value) {
        items.push([
            {
                label: 'Deactivate',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(u),
            },
        ])
    }
    return items
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
            <template #isActive-cell="{ row }">
                <UBadge
                    :label="row.original.isActive ? 'Active' : 'Inactive'"
                    :color="row.original.isActive ? 'success' : 'neutral'"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions v-if="canEdit || canDelete" :items="rowActions(row.original)" />
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
                    <UFormField label="First name" required :error="createErrors.firstName">
                        <UInput v-model="createForm.firstName" placeholder="John" class="w-full" />
                    </UFormField>
                    <UFormField label="Last name" required :error="createErrors.lastName">
                        <UInput v-model="createForm.lastName" placeholder="Doe" class="w-full" />
                    </UFormField>
                </div>
                <UFormField label="Email" required :error="createErrors.email">
                    <UInput
                        v-model="createForm.email"
                        type="email"
                        placeholder="john@company.com"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Password" required :error="createErrors.password">
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

        <!-- Edit user -->
        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit User"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="loadingDetail || !editForm.firstName || !editForm.lastName"
            width-class="sm:max-w-2xl"
            @confirm="submitEdit"
        >
            <div v-if="loadingDetail" class="space-y-3 py-2">
                <USkeleton class="h-9 w-full" />
                <USkeleton class="h-9 w-full" />
            </div>
            <form v-else class="space-y-3" @submit.prevent="submitEdit">
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="First name" required :error="editErrors.firstName">
                        <UInput v-model="editForm.firstName" class="w-full" />
                    </UFormField>
                    <UFormField label="Last name" required :error="editErrors.lastName">
                        <UInput v-model="editForm.lastName" class="w-full" />
                    </UFormField>
                </div>
                <UFormField label="Phone">
                    <UInput v-model="editForm.phone" class="w-full" />
                </UFormField>
                <UFormField label="Roles">
                    <USelect
                        v-model="editForm.roles"
                        :items="roleOptions"
                        multiple
                        placeholder="Select roles"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Groups">
                    <USelect
                        v-model="editForm.groups"
                        :items="groupOptions"
                        multiple
                        placeholder="Select groups"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Record visibility">
                    <USelect
                        v-model="editForm.viewPermission"
                        :items="VIEW_PERMISSIONS"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Set password -->
        <AppConfirmModal
            v-model:open="passwordOpen"
            title="Set Password"
            confirm-label="Update"
            :loading="savingPassword"
            :confirm-disabled="newPassword.length < 8"
            @confirm="submitPassword"
        >
            <form class="space-y-3" @submit.prevent="submitPassword">
                <p class="text-muted text-sm">
                    Set a new password for
                    <strong class="text-highlighted">{{ passwordName }}</strong
                    >.
                </p>
                <UFormField label="New password" required :error="pwErrors.password">
                    <UInput
                        v-model="newPassword"
                        type="password"
                        placeholder="Min 8 characters"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Deactivate User"
            confirm-label="Deactivate"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Deactivate <strong class="text-highlighted">{{ userToDelete?.fullName }}</strong
                >? They will lose access until reactivated.
            </p>
        </AppConfirmModal>
    </div>
</template>
