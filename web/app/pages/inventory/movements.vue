<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import { MOVEMENT_TYPE_COLOR, MOVEMENT_TYPE_LABEL, type MovementResponse } from '~/types/inventory'

definePageMeta({ title: 'Movements' })
useHead({ title: 'Movements — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { productOptions, loadProducts, loadAllLocations, productName, locationLabel } =
    useInventoryLookups()

const productId = ref('')

// ── Ledger query ────────────────────────────────────────────────────────────
const movements = ref<MovementResponse[]>([])
const pending = ref(false)
const hasQueried = ref(false)

async function loadMovements() {
    if (!productId.value) {
        movements.value = []
        hasQueried.value = false
        return
    }
    pending.value = true
    try {
        movements.value = await api<MovementResponse[]>('/api/inventory/movements', {
            params: { productId: productId.value },
        })
        hasQueried.value = true
    } catch (err: unknown) {
        const e = err as { data?: { detail?: string; message?: string } }
        toast.add({
            title: 'Failed to load movements',
            description: e?.data?.detail ?? e?.data?.message,
            color: 'error',
        })
        movements.value = []
    } finally {
        pending.value = false
    }
}

watch(productId, loadMovements)

const columns: TableColumn<MovementResponse>[] = [
    { accessorKey: 'createdAt', header: 'Date' },
    { accessorKey: 'movementType', header: 'Type' },
    { accessorKey: 'quantity', header: 'Qty' },
    { id: 'from', header: 'From' },
    { id: 'to', header: 'To' },
    { id: 'reference', header: 'Reference' },
    { accessorKey: 'notes', header: 'Notes' },
]

function referenceLabel(m: MovementResponse): string {
    if (!m.refDocType) return '—'
    return m.refDocId ? `${m.refDocType} · ${m.refDocId.slice(0, 8)}` : m.refDocType
}

onMounted(() => {
    loadProducts()
    loadAllLocations()
})
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Movements"
            subtitle="Append-only stock movement ledger for a product"
        />

        <UCard>
            <UFormField label="Product" required>
                <USelect
                    v-model="productId"
                    :items="productOptions"
                    placeholder="Select a product to view its ledger"
                    class="w-full sm:max-w-md"
                />
            </UFormField>
        </UCard>

        <AppListTable
            v-if="hasQueried || pending"
            :rows="movements"
            :columns="columns"
            :loading="pending"
        >
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #movementType-cell="{ row }">
                <UBadge
                    :label="
                        MOVEMENT_TYPE_LABEL[row.original.movementType] ?? row.original.movementType
                    "
                    :color="MOVEMENT_TYPE_COLOR[row.original.movementType] ?? 'neutral'"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #quantity-cell="{ row }">
                <span class="text-highlighted text-sm font-medium">{{
                    row.original.quantity
                }}</span>
            </template>
            <template #from-cell="{ row }">
                <span class="text-muted text-sm">{{
                    locationLabel(row.original.fromLocationId)
                }}</span>
            </template>
            <template #to-cell="{ row }">
                <span class="text-muted text-sm">{{
                    locationLabel(row.original.toLocationId)
                }}</span>
            </template>
            <template #reference-cell="{ row }">
                <span class="text-muted text-sm">{{ referenceLabel(row.original) }}</span>
            </template>
            <template #notes-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.notes ?? '—' }}</span>
            </template>
            <template #empty>
                <AppEmptyState
                    icon="i-tabler-list-details"
                    :message="`No movements recorded for ${productName(productId)}`"
                />
            </template>
        </AppListTable>

        <div
            v-else
            class="text-muted border-default rounded-lg border border-dashed py-12 text-center text-sm"
        >
            Select a product to view its movement ledger.
        </div>
    </div>
</template>
