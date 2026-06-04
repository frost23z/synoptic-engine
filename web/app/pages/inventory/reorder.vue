<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { LowStockEntry } from '~/types/inventory'

definePageMeta({ title: 'Reorder' })
useHead({ title: 'Reorder — Synoptic' })

const api = useApi()

/** Low-stock entry with a synthetic row id for AppListTable. */
type ReorderRow = LowStockEntry & { id: string }

const {
    data: entries,
    pending,
    refresh,
} = await useAsyncData<LowStockEntry[]>(
    'inventory-low-stock',
    () => api<LowStockEntry[]>('/api/inventory/low-stock'),
    { default: () => [] }
)

const rows = computed<ReorderRow[]>(() =>
    (entries.value ?? []).map((e) => ({ ...e, id: e.productId }))
)

const columns: TableColumn<ReorderRow>[] = [
    { accessorKey: 'productName', header: 'Product' },
    { accessorKey: 'sku', header: 'SKU' },
    { accessorKey: 'currentStock', header: 'Current stock' },
    { accessorKey: 'reorderThreshold', header: 'Reorder at' },
    { id: 'shortfall', header: 'Shortfall' },
]
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Reorder"
            :subtitle="`${rows.length.toLocaleString()} product${rows.length === 1 ? '' : 's'} at or below threshold`"
        >
            <template #actions>
                <UButton
                    icon="i-tabler-refresh"
                    label="Refresh"
                    color="neutral"
                    variant="outline"
                    :loading="pending"
                    @click="() => refresh()"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="rows" :columns="columns" :loading="pending">
            <template #productName-cell="{ row }">
                <NuxtLink
                    :to="`/products/${row.original.productId}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.productName }}
                </NuxtLink>
            </template>
            <template #sku-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.sku ?? '—' }}</span>
            </template>
            <template #currentStock-cell="{ row }">
                <span class="text-highlighted text-sm font-semibold">{{
                    row.original.currentStock
                }}</span>
            </template>
            <template #reorderThreshold-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.reorderThreshold }}</span>
            </template>
            <template #shortfall-cell="{ row }">
                <UBadge
                    :label="`${Math.max(0, row.original.reorderThreshold - row.original.currentStock)} short`"
                    color="error"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #empty>
                <AppEmptyState
                    icon="i-tabler-circle-check"
                    message="All products are above their reorder threshold"
                />
            </template>
        </AppListTable>
    </div>
</template>
