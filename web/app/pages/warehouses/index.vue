<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { WarehouseResponse } from '~/types/inventory'

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
