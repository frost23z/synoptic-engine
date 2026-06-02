<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'

definePageMeta({ title: 'Attributes' })
useHead({ title: 'Attributes — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

interface AttributeOptionResponse {
    id: string
    attributeId: string
    adminName: string
    sortOrder: number
}

interface AttributeResponse {
    id: string
    code: string
    adminName: string
    type: string
    entityType: string
    lookup?: string
    sortOrder?: number
    userDefined: boolean
    createdAt: string
    updatedAt: string
}

const ATTRIBUTE_TYPES = [
    'TEXT',
    'TEXTAREA',
    'SELECT',
    'MULTISELECT',
    'DATE',
    'DATETIME',
    'BOOLEAN',
    'FILE',
    'IMAGE',
    'PHONE',
    'EMAIL_FIELD',
    'ADDRESS',
]
const ENTITY_TYPES = ['Person', 'Lead', 'Organization', 'Product']

const entityFilter = ref('Person')

const {
    data: attributes,
    pending,
    refresh,
} = await useAsyncData<AttributeResponse[]>(
    () => `attributes-${entityFilter.value}`,
    () =>
        api<AttributeResponse[]>('/api/settings/attributes', {
            params: { entityType: entityFilter.value },
        }),
    { watch: [entityFilter] }
)

// ── Create ────────────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({
    code: '',
    adminName: '',
    type: 'TEXT',
    entityType: 'Person',
    sortOrder: 0,
    userDefined: true,
})

function openCreate() {
    Object.assign(createForm, {
        code: '',
        adminName: '',
        type: 'TEXT',
        entityType: entityFilter.value,
        sortOrder: 0,
        userDefined: true,
    })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/settings/attributes', {
            method: 'POST',
            body: {
                code: createForm.code,
                adminName: createForm.adminName,
                type: createForm.type,
                entityType: createForm.entityType,
                sortOrder: createForm.sortOrder,
                userDefined: createForm.userDefined,
            },
        })
        toast.add({ title: 'Attribute created', color: 'success' })
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
const editTarget = shallowRef<AttributeResponse | null>(null)
const editForm = reactive({ adminName: '', type: 'TEXT', sortOrder: 0 })

function openEdit(a: AttributeResponse) {
    editTarget.value = a
    Object.assign(editForm, { adminName: a.adminName, type: a.type, sortOrder: a.sortOrder ?? 0 })
    editOpen.value = true
}

async function submitEdit() {
    if (!editTarget.value) return
    saving.value = true
    try {
        await api(`/api/settings/attributes/${editTarget.value.id}`, {
            method: 'PUT',
            body: {
                adminName: editForm.adminName,
                type: editForm.type,
                sortOrder: editForm.sortOrder,
            },
        })
        toast.add({ title: 'Attribute saved', color: 'success' })
        editOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: attributeToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<AttributeResponse>({
    endpoint: (a) => `/api/settings/attributes/${a.id}`,
    successMessage: 'Attribute deleted',
    onDeleted: refresh,
})

// ── Options panel (for SELECT / MULTISELECT) ──────────────────────────────
const optionsOpen = ref(false)
const optionsTarget = shallowRef<AttributeResponse | null>(null)
const options = ref<AttributeOptionResponse[]>([])
const optionsPending = ref(false)
const newOptionName = ref('')
const addingOption = ref(false)

async function openOptions(a: AttributeResponse) {
    optionsTarget.value = a
    optionsOpen.value = true
    optionsPending.value = true
    try {
        const result = await api<AttributeOptionResponse[]>(
            `/api/settings/attributes/${a.id}/options`
        )
        options.value = result ?? []
    } catch {
        options.value = []
    } finally {
        optionsPending.value = false
    }
}

async function addOption() {
    if (!optionsTarget.value || !newOptionName.value.trim()) return
    addingOption.value = true
    try {
        const opt = await api<AttributeOptionResponse>(
            `/api/settings/attributes/${optionsTarget.value.id}/options`,
            {
                method: 'POST',
                body: { adminName: newOptionName.value.trim(), sortOrder: options.value.length },
            }
        )
        options.value.push(opt)
        newOptionName.value = ''
        toast.add({ title: 'Option added', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to add option', color: 'error' })
    } finally {
        addingOption.value = false
    }
}

async function deleteOption(optionId: string) {
    if (!optionsTarget.value) return
    try {
        await api(`/api/settings/attributes/${optionsTarget.value.id}/options/${optionId}`, {
            method: 'DELETE',
        })
        options.value = options.value.filter((o) => o.id !== optionId)
        toast.add({ title: 'Option removed', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to remove', color: 'error' })
    }
}

const columns: TableColumn<AttributeResponse>[] = [
    { accessorKey: 'code', header: 'Code' },
    { accessorKey: 'adminName', header: 'Name' },
    { id: 'type', header: 'Type' },
    { id: 'meta', header: 'Meta' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(a: AttributeResponse): DropdownMenuItem[][] {
    const primary: DropdownMenuItem[] = []
    if (can('attributes.edit')) {
        primary.push({ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(a) })
        if (a.type === 'SELECT' || a.type === 'MULTISELECT') {
            primary.push({
                label: 'Options',
                icon: 'i-tabler-list',
                onSelect: () => openOptions(a),
            })
        }
    }
    const items: DropdownMenuItem[][] = primary.length ? [primary] : []
    if (a.userDefined && can('attributes.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(a),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Attributes" subtitle="Custom fields for CRM entities">
            <template #actions>
                <UButton
                    v-if="can('attributes.create')"
                    icon="i-tabler-plus"
                    label="New Attribute"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <!-- Entity type filter -->
        <div class="flex gap-2">
            <UButton
                v-for="et in ENTITY_TYPES"
                :key="et"
                :label="et"
                size="sm"
                :color="entityFilter === et ? 'primary' : 'neutral'"
                :variant="entityFilter === et ? 'solid' : 'outline'"
                @click="entityFilter = et"
            />
        </div>

        <AppListTable :rows="attributes ?? []" :columns="columns" :loading="pending">
            <template #code-cell="{ row }">
                <code class="bg-muted rounded px-1.5 py-0.5 text-xs">{{ row.original.code }}</code>
            </template>
            <template #adminName-cell="{ row }">
                <span class="font-medium">{{ row.original.adminName }}</span>
            </template>
            <template #type-cell="{ row }">
                <UBadge :label="row.original.type" color="neutral" variant="soft" size="sm" />
            </template>
            <template #meta-cell="{ row }">
                <div class="flex gap-1">
                    <UBadge
                        v-if="row.original.userDefined"
                        label="Custom"
                        color="primary"
                        variant="soft"
                        size="xs"
                    />
                    <UBadge v-else label="System" color="neutral" variant="soft" size="xs" />
                    <UBadge
                        v-if="row.original.sortOrder != null"
                        :label="`#${row.original.sortOrder}`"
                        color="neutral"
                        variant="soft"
                        size="xs"
                    />
                </div>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions
                    v-if="
                        can('attributes.edit') ||
                        (row.original.userDefined && can('attributes.delete'))
                    "
                    :items="rowActions(row.original)"
                />
            </template>
            <template #empty>
                <AppEmptyState
                    icon="i-tabler-database-off"
                    :message="`No attributes for ${entityFilter}`"
                />
            </template>
        </AppListTable>

        <!-- Create modal -->
        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Attribute"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.code || !createForm.adminName"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Code" required>
                        <UInput
                            v-model="createForm.code"
                            placeholder="e.g. custom_field"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Name" required>
                        <UInput
                            v-model="createForm.adminName"
                            placeholder="Display name"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Type" required>
                        <USelect
                            v-model="createForm.type"
                            :items="ATTRIBUTE_TYPES.map((t) => ({ label: t, value: t }))"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Entity" required>
                        <USelect
                            v-model="createForm.entityType"
                            :items="ENTITY_TYPES.map((t) => ({ label: t, value: t }))"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <UFormField label="Sort Order">
                    <UInput
                        v-model.number="createForm.sortOrder"
                        type="number"
                        min="0"
                        class="w-full"
                    />
                </UFormField>
                <USwitch v-model="createForm.userDefined" label="User-defined (custom)" />
            </form>
        </AppConfirmModal>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit Attribute"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.adminName"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <UFormField label="Name" required>
                    <UInput v-model="editForm.adminName" class="w-full" />
                </UFormField>
                <UFormField label="Type" required>
                    <USelect
                        v-model="editForm.type"
                        :items="ATTRIBUTE_TYPES.map((t) => ({ label: t, value: t }))"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Sort Order">
                    <UInput
                        v-model.number="editForm.sortOrder"
                        type="number"
                        min="0"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Options panel (bespoke) -->
        <UModal v-model:open="optionsOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">
                                {{ optionsTarget?.adminName }} — Options
                            </p>
                            <UButton
                                icon="i-tabler-x"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                @click="optionsOpen = false"
                            />
                        </div>
                    </template>
                    <div class="mb-4 flex gap-2">
                        <UInput
                            v-model="newOptionName"
                            placeholder="Option name"
                            class="flex-1"
                            @keydown.enter="addOption"
                        />
                        <UButton
                            icon="i-tabler-plus"
                            label="Add"
                            :loading="addingOption"
                            :disabled="!newOptionName.trim()"
                            @click="addOption"
                        />
                    </div>
                    <div v-if="optionsPending" class="space-y-2">
                        <USkeleton v-for="i in 3" :key="i" class="h-9 w-full" />
                    </div>
                    <div
                        v-else-if="options.length === 0"
                        class="text-muted py-6 text-center text-sm"
                    >
                        No options yet
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="(opt, idx) in options"
                            :key="opt.id"
                            class="flex items-center justify-between py-2"
                        >
                            <div class="flex items-center gap-2">
                                <span class="text-muted text-xs">{{ idx + 1 }}</span>
                                <span class="text-sm font-medium">{{ opt.adminName }}</span>
                            </div>
                            <UButton
                                icon="i-tabler-trash"
                                color="error"
                                variant="ghost"
                                size="xs"
                                @click="deleteOption(opt.id)"
                            />
                        </li>
                    </ul>
                </UCard>
            </template>
        </UModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Attribute"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ attributeToDelete?.adminName }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
