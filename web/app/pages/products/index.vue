<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { ProductResponse } from '~/types/inventory'

definePageMeta({ title: 'Products' })
useHead({ title: 'Products — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatCurrency, formatDate } = useFormatters()
const { can } = usePermissions()

const { selected, selectAll, clearAll, count } = useMassSelect()

const {
    page,
    search,
    items: products,
    total,
    pending,
    refresh,
} = await usePaginatedList<ProductResponse>('/api/products', { key: 'products' })

const massDeleting = ref(false)
async function massDelete() {
    if (!selected.value.length) return
    massDeleting.value = true
    try {
        await api('/api/products/mass-destroy', { method: 'POST', body: { ids: selected.value } })
        toast.add({ title: `${count.value} products deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

const {
    open: deleteOpen,
    target: productToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<ProductResponse>({
    endpoint: (p) => `/api/products/${p.id}`,
    successMessage: 'Product deleted',
    onDeleted: refresh,
})

const columns: TableColumn<ProductResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'sku', header: 'SKU' },
    { accessorKey: 'price', header: 'Price' },
    { accessorKey: 'active', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(p: ProductResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [{ label: 'View', icon: 'i-tabler-eye', onSelect: () => router.push(`/products/${p.id}`) }],
    ]
    if (can('products.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(p),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Products" :subtitle="`${total.toLocaleString()} total`">
            <template #actions>
                <UButton
                    v-if="can('products.create')"
                    icon="i-tabler-plus"
                    label="New Product"
                    to="/products/create"
                />
            </template>
        </AppPageHeader>

        <UInput
            v-model="search"
            placeholder="Search products…"
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
            :rows="products"
            :columns="columns"
            :loading="pending"
            selectable
            :selected="selected"
            @update:selected="selectAll"
        >
            <template #name-cell="{ row }">
                <NuxtLink
                    :to="`/products/${row.original.id}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.name }}
                </NuxtLink>
            </template>
            <template #sku-cell="{ row }">
                <span class="text-muted font-mono text-xs">{{ row.original.sku ?? '—' }}</span>
            </template>
            <template #price-cell="{ row }">
                <span class="font-semibold">{{ formatCurrency(row.original.price) }}</span>
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
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-box-off" message="No products found" />
            </template>
        </AppListTable>

        <AppPagination v-model:page="page" :total="total" />

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Product"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ productToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
