<script setup lang="ts">
import type { ActivitiesPage, ActivityResponse } from '~/types/activities'
import type { TagResponse } from '~/types/leads'
import type {
    WarehouseLocationResponse,
    WarehouseProductEntry,
    WarehouseResponse,
} from '~/types/inventory'

definePageMeta({ title: 'Warehouse' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const { loadProducts, productName } = useInventoryLookups()
const id = route.params.id as string

const {
    data: warehouse,
    pending,
    refresh,
} = await useAsyncData<WarehouseResponse>(`warehouse-${id}`, () =>
    api<WarehouseResponse>(`/api/warehouses/${id}`)
)

const pageTitle = computed(() =>
    warehouse.value?.name ? `${warehouse.value.name} — Synoptic` : 'Warehouse — Synoptic'
)
useHead({ title: pageTitle })

const canEdit = computed(() => can('warehouses.edit'))

// ── Locations ───────────────────────────────────────────────────────────────
const locations = ref<WarehouseLocationResponse[]>([])
const locationsPending = ref(true)
const newLocationName = ref('')
const addingLocation = ref(false)

const locationNameById = computed(() =>
    Object.fromEntries(locations.value.map((l) => [l.id, l.name]))
)

async function loadLocations() {
    locationsPending.value = true
    try {
        locations.value = await api<WarehouseLocationResponse[]>(`/api/warehouses/${id}/locations`)
    } finally {
        locationsPending.value = false
    }
}

async function addLocation() {
    const name = newLocationName.value.trim()
    if (!name) return
    addingLocation.value = true
    try {
        const loc = await api<WarehouseLocationResponse>(`/api/warehouses/${id}/locations`, {
            method: 'POST',
            body: { name },
        })
        locations.value.push(loc)
        newLocationName.value = ''
        toast.add({ title: 'Location added', color: 'success' })
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to add location',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        addingLocation.value = false
    }
}

// Rename location
const renameOpen = ref(false)
const renaming = ref(false)
const renameTarget = shallowRef<WarehouseLocationResponse | null>(null)
const renameValue = ref('')

function promptRename(loc: WarehouseLocationResponse) {
    renameTarget.value = loc
    renameValue.value = loc.name
    renameOpen.value = true
}

async function submitRename() {
    const target = renameTarget.value
    const name = renameValue.value.trim()
    if (!target || !name) return
    renaming.value = true
    try {
        const updated = await api<WarehouseLocationResponse>(
            `/api/warehouses/${id}/locations/${target.id}`,
            { method: 'PUT', body: { name } }
        )
        locations.value = locations.value.map((l) => (l.id === updated.id ? updated : l))
        renameOpen.value = false
        toast.add({ title: 'Location renamed', color: 'success' })
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to rename', description: e?.data?.message, color: 'error' })
    } finally {
        renaming.value = false
    }
}

async function deleteLocation(loc: WarehouseLocationResponse) {
    try {
        await api(`/api/warehouses/${id}/locations/${loc.id}`, { method: 'DELETE' })
        locations.value = locations.value.filter((l) => l.id !== loc.id)
        toast.add({ title: 'Location removed', color: 'success' })
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to remove', description: e?.data?.message, color: 'error' })
    }
}

// ── Stock by product ────────────────────────────────────────────────────────
const { data: productEntries } = await useAsyncData<WarehouseProductEntry[]>(
    `warehouse-${id}-products`,
    () => api<WarehouseProductEntry[]>(`/api/warehouses/${id}/products`),
    { default: () => [] }
)

// ── Activities ──────────────────────────────────────────────────────────────
const { data: activities } = await useAsyncData<ActivityResponse[]>(
    `warehouse-${id}-activities`,
    () => api<ActivitiesPage>(`/api/warehouses/${id}/activities`).then((p) => p.content),
    { default: () => [] }
)

// ── Tags ────────────────────────────────────────────────────────────────────
const { data: allTags } = await useAsyncData<TagResponse[]>(`warehouse-tags-all`, () =>
    can('tags.view') ? api<TagResponse[]>('/api/tags') : Promise.resolve([])
)

// ── Edit ────────────────────────────────────────────────────────────────────
const editing = ref(false)
const saving = ref(false)
const editForm = reactive({
    name: '',
    description: '',
    contactName: '',
    contactEmail: '',
    contactPhone: '',
    contactAddress: '',
})

function openEdit() {
    if (!warehouse.value) return
    Object.assign(editForm, {
        name: warehouse.value.name,
        description: warehouse.value.description ?? '',
        contactName: warehouse.value.contactName ?? '',
        contactEmail: warehouse.value.contactEmail ?? '',
        contactPhone: warehouse.value.contactPhone ?? '',
        contactAddress: warehouse.value.contactAddress ?? '',
    })
    editing.value = true
}

async function submitEdit() {
    saving.value = true
    try {
        await api(`/api/warehouses/${id}`, {
            method: 'PUT',
            body: {
                name: editForm.name,
                description: editForm.description || undefined,
                contactName: editForm.contactName || undefined,
                contactEmail: editForm.contactEmail || undefined,
                contactPhone: editForm.contactPhone || undefined,
                contactAddress: editForm.contactAddress || undefined,
            },
        })
        toast.add({ title: 'Saved', color: 'success' })
        editing.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ──────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<WarehouseResponse>({
    endpoint: (w) => `/api/warehouses/${w.id}`,
    successMessage: 'Warehouse deleted',
    onDeleted: () => {
        router.push('/warehouses')
    },
})

onMounted(() => {
    loadLocations()
    loadProducts()
})
</script>

<template>
    <AppDetailLayout
        v-if="warehouse"
        to="/warehouses"
        :title="warehouse.name"
        :subtitle="warehouse.description || 'Warehouse'"
    >
        <template #actions>
            <UButton
                v-if="canEdit"
                icon="i-tabler-pencil"
                label="Edit"
                color="neutral"
                variant="outline"
                @click="openEdit"
            />
            <UButton
                v-if="can('warehouses.delete')"
                icon="i-tabler-trash"
                color="error"
                variant="outline"
                @click="promptDelete(warehouse)"
            />
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div class="space-y-6 lg:col-span-2">
                <!-- Details -->
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Details</p></template
                    >
                    <dl class="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                        <div>
                            <dt class="text-muted">Contact name</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ warehouse.contactName ?? '—' }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Contact email</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ warehouse.contactEmail ?? '—' }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Contact phone</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ warehouse.contactPhone ?? '—' }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Created</dt>
                            <dd class="text-muted mt-0.5">{{ formatDate(warehouse.createdAt) }}</dd>
                        </div>
                        <div v-if="warehouse.contactAddress" class="col-span-2">
                            <dt class="text-muted">Address</dt>
                            <dd class="text-highlighted mt-0.5">{{ warehouse.contactAddress }}</dd>
                        </div>
                        <div v-if="warehouse.description" class="col-span-2">
                            <dt class="text-muted">Description</dt>
                            <dd class="text-highlighted mt-0.5">{{ warehouse.description }}</dd>
                        </div>
                    </dl>
                </UCard>

                <!-- Locations -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Locations</p>
                    </template>
                    <div v-if="canEdit" class="mb-4 flex gap-2">
                        <UInput
                            v-model="newLocationName"
                            placeholder="Location name"
                            class="flex-1"
                            @keydown.enter="addLocation"
                        />
                        <UButton
                            icon="i-tabler-plus"
                            label="Add"
                            :loading="addingLocation"
                            :disabled="!newLocationName.trim()"
                            @click="addLocation"
                        />
                    </div>
                    <div v-if="locationsPending" class="space-y-2">
                        <USkeleton v-for="i in 3" :key="i" class="h-9 w-full" />
                    </div>
                    <div v-else-if="!locations.length" class="text-muted py-6 text-center text-sm">
                        No locations yet
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="loc in locations"
                            :key="loc.id"
                            class="flex items-center justify-between py-2"
                        >
                            <div class="flex items-center gap-2">
                                <UIcon name="i-tabler-map-pin" class="text-muted size-4" />
                                <span class="text-sm">{{ loc.name }}</span>
                            </div>
                            <div class="flex items-center gap-1">
                                <UButton
                                    v-if="canEdit"
                                    icon="i-tabler-pencil"
                                    color="neutral"
                                    variant="ghost"
                                    size="xs"
                                    @click="promptRename(loc)"
                                />
                                <UButton
                                    v-if="can('warehouses.delete')"
                                    icon="i-tabler-trash"
                                    color="error"
                                    variant="ghost"
                                    size="xs"
                                    @click="deleteLocation(loc)"
                                />
                            </div>
                        </li>
                    </ul>
                </UCard>

                <!-- Stock -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Stock</p>
                    </template>
                    <div v-if="!productEntries.length" class="text-muted py-6 text-center text-sm">
                        No stock recorded in this warehouse
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="(entry, i) in productEntries"
                            :key="`${entry.productId}-${entry.warehouseLocationId ?? i}`"
                            class="flex items-center justify-between gap-3 py-2 text-sm"
                        >
                            <div class="min-w-0">
                                <NuxtLink
                                    :to="`/products/${entry.productId}`"
                                    class="text-primary font-medium hover:underline"
                                >
                                    {{ productName(entry.productId) }}
                                </NuxtLink>
                                <p class="text-muted text-xs">
                                    {{
                                        entry.warehouseLocationId
                                            ? (locationNameById[entry.warehouseLocationId] ??
                                              'Unassigned')
                                            : 'Unassigned'
                                    }}
                                </p>
                            </div>
                            <span class="text-highlighted font-semibold">{{ entry.quantity }}</span>
                        </li>
                    </ul>
                </UCard>
            </div>

            <div class="space-y-6">
                <!-- Tags -->
                <AppTagManager
                    :tags="warehouse.tags ?? []"
                    :all-tags="allTags ?? []"
                    :endpoint="`/api/warehouses/${id}/tags`"
                    :can-edit="canEdit"
                    @changed="refresh"
                />

                <!-- Activities -->
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Activities</p>
                    </template>
                    <EntityTimeline :activities="activities ?? []" />
                </UCard>
            </div>
        </div>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editing"
            title="Edit Warehouse"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.name.trim()"
            width-class="sm:max-w-2xl"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <UFormField label="Name" required>
                    <UInput v-model="editForm.name" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UTextarea v-model="editForm.description" :rows="3" class="w-full" />
                </UFormField>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Contact name">
                        <UInput v-model="editForm.contactName" class="w-full" />
                    </UFormField>
                    <UFormField label="Contact email">
                        <UInput v-model="editForm.contactEmail" type="email" class="w-full" />
                    </UFormField>
                    <UFormField label="Contact phone">
                        <UInput v-model="editForm.contactPhone" class="w-full" />
                    </UFormField>
                    <UFormField label="Contact address">
                        <UInput v-model="editForm.contactAddress" class="w-full" />
                    </UFormField>
                </div>
            </form>
        </AppConfirmModal>

        <!-- Rename location modal -->
        <AppConfirmModal
            v-model:open="renameOpen"
            title="Rename location"
            confirm-label="Save"
            :loading="renaming"
            :confirm-disabled="!renameValue.trim()"
            @confirm="submitRename"
        >
            <form @submit.prevent="submitRename">
                <UFormField label="Name" required>
                    <UInput v-model="renameValue" class="w-full" autofocus />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Warehouse"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ warehouse.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </AppDetailLayout>

    <div v-else-if="pending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <div class="grid grid-cols-3 gap-6">
            <USkeleton class="col-span-2 h-40 w-full" />
            <USkeleton class="h-40 w-full" />
        </div>
    </div>
</template>
