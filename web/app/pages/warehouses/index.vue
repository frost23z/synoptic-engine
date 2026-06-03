<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { ActivityResponse } from '~/types/activities'
import { ACTIVITY_TYPE_ICON } from '~/types/activities'
import type {
    WarehouseLocationResponse,
    WarehouseResponse,
    WarehouseTagResponse,
} from '~/types/inventory'

definePageMeta({ title: 'Warehouses' })
useHead({ title: 'Warehouses — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const {
    page,
    search,
    items: warehouses,
    total,
    pending,
    refresh,
} = await usePaginatedList<WarehouseResponse>('/api/warehouses', { key: 'warehouses' })

const { selected, selectAll, clearAll, count } = useMassSelect()

// ── Locations panel ───────────────────────────────────────────────────────
const locationsOpen = ref(false)
const selectedWarehouse = shallowRef<WarehouseResponse | null>(null)
const locations = ref<WarehouseLocationResponse[]>([])
const locationsPending = ref(false)
const newLocationName = ref('')
const addingLocation = ref(false)

async function openLocations(w: WarehouseResponse) {
    selectedWarehouse.value = w
    locationsOpen.value = true
    locationsPending.value = true
    loadWarehouseActivities(w.id)
    warehouseTags.value = []
    try {
        locations.value = await api<WarehouseLocationResponse[]>(
            `/api/warehouses/${w.id}/locations`
        )
    } finally {
        locationsPending.value = false
    }
}

async function addLocation() {
    if (!selectedWarehouse.value || !newLocationName.value.trim()) return
    addingLocation.value = true
    try {
        const loc = await api<WarehouseLocationResponse>(
            `/api/warehouses/${selectedWarehouse.value.id}/locations`,
            { method: 'POST', body: { name: newLocationName.value.trim() } }
        )
        locations.value.push(loc)
        newLocationName.value = ''
        toast.add({ title: 'Location added', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to add location', color: 'error' })
    } finally {
        addingLocation.value = false
    }
}

async function deleteLocation(locationId: string) {
    if (!selectedWarehouse.value) return
    try {
        await api(`/api/warehouses/${selectedWarehouse.value.id}/locations/${locationId}`, {
            method: 'DELETE',
        })
        locations.value = locations.value.filter((l) => l.id !== locationId)
        toast.add({ title: 'Location removed', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to remove location', color: 'error' })
    }
}

// ── Warehouse activities ───────────────────────────────────────────────
const warehouseActivities = ref<ActivityResponse[]>([])
const activitiesPending = ref(false)

async function loadWarehouseActivities(warehouseId: string) {
    activitiesPending.value = true
    try {
        warehouseActivities.value = await api<ActivityResponse[]>(
            `/api/warehouses/${warehouseId}/activities`
        )
    } finally {
        activitiesPending.value = false
    }
}

// ── Warehouse tags ─────────────────────────────────────────────────────
const warehouseTags = ref<WarehouseTagResponse[]>([])
const { data: allTags } = await useAsyncData<WarehouseTagResponse[]>('all-tags-wh', () =>
    api<WarehouseTagResponse[]>('/api/tags')
)
const tagToAdd = ref('')
const addingTag = ref(false)

async function addWarehouseTag() {
    if (!selectedWarehouse.value || !tagToAdd.value) return
    addingTag.value = true
    try {
        await api(`/api/warehouses/${selectedWarehouse.value.id}/tags`, {
            method: 'POST',
            body: { tagId: tagToAdd.value },
        })
        const tag = allTags.value?.find((t) => t.id === tagToAdd.value)
        if (tag) warehouseTags.value.push(tag)
        tagToAdd.value = ''
        toast.add({ title: 'Tag added', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to add tag', color: 'error' })
    } finally {
        addingTag.value = false
    }
}

async function removeWarehouseTag(tagId: string) {
    if (!selectedWarehouse.value) return
    try {
        await api(`/api/warehouses/${selectedWarehouse.value.id}/tags/${tagId}`, {
            method: 'DELETE',
        })
        warehouseTags.value = warehouseTags.value.filter((t) => t.id !== tagId)
        toast.add({ title: 'Tag removed', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to remove tag', color: 'error' })
    }
}

// ── Mass delete ───────────────────────────────────────────────────────────
const massDeleting = ref(false)
async function massDelete() {
    if (!selected.value.length) return
    massDeleting.value = true
    try {
        await api('/api/warehouses/mass-destroy', { method: 'POST', body: { ids: selected.value } })
        toast.add({ title: `${count.value} warehouses deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

// ── Delete warehouse ──────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: warehouseToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<WarehouseResponse>({
    endpoint: (w) => `/api/warehouses/${w.id}`,
    successMessage: 'Warehouse deleted',
    onDeleted: refresh,
})

const columns: TableColumn<WarehouseResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'contactName', header: 'Contact' },
    { accessorKey: 'contactEmail', header: 'Email' },
    { accessorKey: 'contactPhone', header: 'Phone' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(w: WarehouseResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                onSelect: () => router.push(`/warehouses/${w.id}`),
            },
            { label: 'Locations', icon: 'i-tabler-map-pin', onSelect: () => openLocations(w) },
        ],
    ]
    if (can('warehouses.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(w),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Warehouses" :subtitle="`${total.toLocaleString()} total`">
            <template #actions>
                <UButton
                    v-if="can('warehouses.create')"
                    icon="i-tabler-plus"
                    label="New Warehouse"
                    to="/warehouses/create"
                />
            </template>
        </AppPageHeader>

        <UInput
            v-model="search"
            placeholder="Search warehouses…"
            icon="i-tabler-search"
            class="w-64"
        />

        <AppMassActionBar :count="count" @clear="clearAll">
            <UButton
                icon="i-tabler-trash"
                label="Delete"
                size="sm"
                color="error"
                variant="soft"
                :loading="massDeleting"
                @click="massDelete"
            />
        </AppMassActionBar>

        <AppListTable
            :rows="warehouses"
            :columns="columns"
            :loading="pending"
            selectable
            :selected="selected"
            @update:selected="selectAll"
        >
            <template #name-cell="{ row }">
                <NuxtLink
                    :to="`/warehouses/${row.original.id}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.name }}
                </NuxtLink>
            </template>
            <template #contactName-cell="{ row }">
                <span class="text-sm">{{ row.original.contactName ?? '—' }}</span>
            </template>
            <template #contactEmail-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.contactEmail ?? '—' }}</span>
            </template>
            <template #contactPhone-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.contactPhone ?? '—' }}</span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-building-warehouse" message="No warehouses found" />
            </template>
        </AppListTable>

        <AppPagination v-model:page="page" :total="total" />

        <!-- Locations / activities / tags panel (bespoke) -->
        <UModal v-model:open="locationsOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">
                                {{ selectedWarehouse?.name }} — Locations
                            </p>
                            <UButton
                                icon="i-tabler-x"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                @click="locationsOpen = false"
                            />
                        </div>
                    </template>

                    <!-- Add location form -->
                    <div class="mb-4 flex gap-2">
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

                    <!-- Locations list -->
                    <div v-if="locationsPending" class="space-y-2">
                        <USkeleton v-for="i in 3" :key="i" class="h-9 w-full" />
                    </div>
                    <div
                        v-else-if="locations.length === 0"
                        class="text-muted py-6 text-center text-sm"
                    >
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
                            <UButton
                                icon="i-tabler-trash"
                                color="error"
                                variant="ghost"
                                size="xs"
                                @click="deleteLocation(loc.id)"
                            />
                        </li>
                    </ul>

                    <!-- Activities -->
                    <div class="mt-4 space-y-2">
                        <p class="text-muted text-xs font-semibold uppercase">Activities</p>
                        <div v-if="activitiesPending">
                            <USkeleton v-for="i in 2" :key="i" class="mb-1 h-8 w-full" />
                        </div>
                        <div
                            v-else-if="warehouseActivities.length === 0"
                            class="text-muted py-2 text-sm"
                        >
                            No activities
                        </div>
                        <div
                            v-for="act in warehouseActivities"
                            :key="act.id"
                            class="border-default rounded-lg border p-2"
                        >
                            <div class="flex items-center gap-2">
                                <UIcon
                                    :name="ACTIVITY_TYPE_ICON[act.type]"
                                    class="text-muted size-4"
                                />
                                <p class="text-sm">{{ act.title }}</p>
                            </div>
                        </div>
                    </div>

                    <!-- Tags -->
                    <div class="mt-4 space-y-2">
                        <p class="text-muted text-xs font-semibold uppercase">Tags</p>
                        <div class="flex gap-2">
                            <USelect
                                v-model="tagToAdd"
                                :items="
                                    (allTags ?? []).map((t) => ({ label: t.name, value: t.id }))
                                "
                                placeholder="Add tag…"
                                class="flex-1"
                            />
                            <UButton
                                icon="i-tabler-plus"
                                size="sm"
                                :loading="addingTag"
                                :disabled="!tagToAdd"
                                @click="addWarehouseTag"
                            />
                        </div>
                        <div class="flex flex-wrap gap-1">
                            <UBadge
                                v-for="tag in warehouseTags"
                                :key="tag.id"
                                :label="tag.name"
                                color="neutral"
                                variant="soft"
                            >
                                <template #trailing>
                                    <button
                                        class="ml-1 cursor-pointer"
                                        @click="removeWarehouseTag(tag.id)"
                                    >
                                        <UIcon name="i-tabler-x" class="size-3" />
                                    </button>
                                </template>
                            </UBadge>
                        </div>
                    </div>
                </UCard>
            </template>
        </UModal>

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
                Delete <strong class="text-highlighted">{{ warehouseToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
