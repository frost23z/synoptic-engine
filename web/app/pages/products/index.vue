<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ProductResponse, ProductsPage } from '~/types/inventory'

definePageMeta({ title: 'Products' })
useHead({ title: 'Products — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatCurrency, formatDate } = useFormatters()
const { can } = usePermissions()

const page = ref(1)
const PAGE_SIZE = 20
const search = ref('')
const debouncedSearch = refDebounced(search, 300)

const queryKey = computed(() => ['products', page.value, debouncedSearch.value])

const {
    data: productsPage,
    pending,
    refresh,
} = await useAsyncData<ProductsPage>(
    () => queryKey.value.join('-'),
    () => {
        const params: Record<string, string | number> = { page: page.value - 1, size: PAGE_SIZE }
        if (debouncedSearch.value) params.q = debouncedSearch.value
        return api<ProductsPage>('/api/products', { params })
    },
    { watch: [queryKey] }
)

const products = computed(() => productsPage.value?.content ?? [])
const total = computed(() => productsPage.value?.totalElements ?? 0)

// ── Mass delete ───────────────────────────────────────────────────────────
const { selected, isSelected, toggle, selectAll, clearAll, hasSelection, count } = useMassSelect()
const massDeleting = ref(false)

async function massDelete() {
    if (!hasSelection.value) return
    massDeleting.value = true
    try {
        await api('/api/products/mass-destroy', {
            method: 'POST',
            body: { ids: selected.value },
        })
        toast.add({ title: `${count.value} products deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<ProductResponse | null>(null)
const deleting = ref(false)

function openDeleteModal(p: ProductResponse) {
    toDelete.value = p
    deleteOpen.value = true
}

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/products/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Product deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Table columns ─────────────────────────────────────────────────────────
const columns: TableColumn<ProductResponse>[] = [
    { id: 'select', header: '', meta: { class: { th: 'w-8', td: 'w-8' } } },
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'sku', header: 'SKU' },
    { accessorKey: 'price', header: 'Price' },
    { accessorKey: 'active', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(p: ProductResponse) {
    return [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                click: () => router.push(`/products/${p.id}`),
            },
        ],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => openDeleteModal(p),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <!-- Header -->
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Products</h2>
                <p class="text-muted text-sm">{{ total.toLocaleString() }} total</p>
            </div>
            <UButton
                v-if="can('products.create')"
                icon="i-tabler-plus"
                label="New Product"
                @click="router.push('/products/create')"
            />
        </div>

        <!-- Search -->
        <UInput
            v-model="search"
            placeholder="Search products…"
            icon="i-tabler-search"
            class="w-64"
        />

        <!-- Mass action bar -->
        <div
            v-if="hasSelection"
            class="bg-default border-default flex items-center gap-3 rounded-lg border px-4 py-2"
        >
            <span class="text-muted text-sm">{{ count }} selected</span>
            <UButton
                icon="i-tabler-trash"
                label="Delete"
                size="sm"
                color="error"
                variant="soft"
                :loading="massDeleting"
                @click="massDelete"
            />
            <UButton label="Clear" size="sm" color="neutral" variant="ghost" @click="clearAll" />
        </div>

        <!-- Table -->
        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="products" :columns="columns" :loading="pending" sticky>
                <template #select-header>
                    <UCheckbox
                        :checked="products.length > 0 && selected.length === products.length"
                        :indeterminate="selected.length > 0 && selected.length < products.length"
                        @change="
                            products.length === selected.length
                                ? clearAll()
                                : selectAll(products.map((i) => i.id))
                        "
                    />
                </template>
                <template #select-cell="{ row }">
                    <UCheckbox
                        :checked="isSelected(row.original.id)"
                        @change="toggle(row.original.id)"
                    />
                </template>

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
                        <UIcon name="i-tabler-box-off" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No products found</p>
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

        <!-- Delete modal -->
        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Delete Product</p>
                    </template>
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
