<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { StockStateResponse } from '~/types/inventory'

/** Stock state with a synthetic row id (location-scoped) for AppListTable. */
type StockRow = StockStateResponse & { id: string }

definePageMeta({ title: 'Stock Levels' })
useHead({ title: 'Stock Levels — Synoptic' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const {
    submitting,
    errors,
    run: runAction,
    validate: validateAction,
    clearErrors: clearActionErrors,
} = useFormSubmit({ failureTitle: 'Stock action failed' })
const {
    productOptions,
    warehouseOptions,
    locationsByWarehouse,
    loadProducts,
    loadWarehouses,
    ensureLocations,
} = useInventoryLookups()

const canCreate = can('inventory.movements.create')

const productId = ref('')
const warehouseId = ref('')
const locationId = ref<string | undefined>(undefined)

const locationNameById = computed(() => {
    const list = warehouseId.value ? (locationsByWarehouse.value[warehouseId.value] ?? []) : []
    return Object.fromEntries(list.map((l) => [l.id, l.name]))
})
const locationOptions = computed(() => [
    { label: 'All locations', value: undefined },
    ...(warehouseId.value ? (locationsByWarehouse.value[warehouseId.value] ?? []) : []).map(
        (l) => ({
            label: l.name,
            value: l.id,
        })
    ),
])

function locationName(id?: string | null): string {
    if (!id) return 'Unassigned'
    return locationNameById.value[id] ?? id.slice(0, 8)
}

// ── Stock query ─────────────────────────────────────────────────────────────
const stock = ref<StockRow[]>([])
const stockPending = ref(false)
const hasQueried = ref(false)

async function loadStock() {
    if (!productId.value || !warehouseId.value) {
        stock.value = []
        hasQueried.value = false
        return
    }
    stockPending.value = true
    try {
        const res = await api<StockStateResponse[]>('/api/inventory/stock', {
            params: {
                productId: productId.value,
                warehouseId: warehouseId.value,
                ...(locationId.value ? { locationId: locationId.value } : {}),
            },
        })
        stock.value = res.map((s) => ({ ...s, id: s.locationId ?? 'unassigned' }))
        hasQueried.value = true
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to load stock', description: e?.data?.message, color: 'error' })
        stock.value = []
    } finally {
        stockPending.value = false
    }
}

watch(warehouseId, async (id) => {
    locationId.value = undefined
    if (id) await ensureLocations(id)
    loadStock()
})
watch([productId, locationId], loadStock)

const columns = computed<TableColumn<StockRow>[]>(() => {
    const cols: TableColumn<StockRow>[] = [
        { id: 'location', header: 'Location' },
        { accessorKey: 'onHand', header: 'On hand' },
        { accessorKey: 'reserved', header: 'Reserved' },
        { accessorKey: 'inTransit', header: 'In transit' },
        { accessorKey: 'damaged', header: 'Damaged' },
        { accessorKey: 'available', header: 'Available' },
    ]
    if (canCreate)
        cols.push({ id: 'actions', header: '', meta: { class: { th: 'w-32', td: 'w-32' } } })
    return cols
})

// ── Reserve / release ─────────────────────────────────────────────────────
const actionOpen = ref(false)
const actionMode = ref<'reserve' | 'release'>('reserve')
const actionRow = shallowRef<StockRow | null>(null)
const actionQty = ref(1)

const qtyAtLeastOne = (v: unknown) =>
    typeof v !== 'number' || Number.isNaN(v) || v < 1 ? 'Enter a quantity of at least 1' : undefined

function openAction(mode: 'reserve' | 'release', row: StockRow) {
    clearActionErrors()
    actionMode.value = mode
    actionRow.value = row
    actionQty.value = 1
    actionOpen.value = true
}

function submitAction() {
    const row = actionRow.value
    if (!row || !row.locationId) return
    runAction({
        validate: () => validateAction({ qty: actionQty.value }, { qty: [qtyAtLeastOne] }),
        call: () =>
            api(`/api/inventory/${actionMode.value}`, {
                method: 'POST',
                body: {
                    productId: row.productId,
                    locationId: row.locationId,
                    qty: actionQty.value,
                },
            }),
        fieldHints: ['qty'],
        onSuccess: () => {
            toast.add({
                title: actionMode.value === 'reserve' ? 'Stock reserved' : 'Stock released',
                color: 'success',
            })
            actionOpen.value = false
            loadStock()
        },
    })
}

onMounted(() => {
    loadProducts()
    loadWarehouses()
})
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Stock Levels"
            subtitle="On-hand, reserved and available stock by location"
        />

        <UCard>
            <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <UFormField label="Product" required>
                    <USelect
                        v-model="productId"
                        :items="productOptions"
                        placeholder="Select product"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Warehouse" required>
                    <USelect
                        v-model="warehouseId"
                        :items="warehouseOptions"
                        placeholder="Select warehouse"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Location">
                    <USelect
                        v-model="locationId"
                        :items="locationOptions"
                        :disabled="!warehouseId"
                        class="w-full"
                    />
                </UFormField>
            </div>
        </UCard>

        <AppListTable
            v-if="hasQueried || stockPending"
            :rows="stock"
            :columns="columns"
            :loading="stockPending"
        >
            <template #location-cell="{ row }">
                <span class="text-highlighted text-sm font-medium">{{
                    locationName(row.original.locationId)
                }}</span>
            </template>
            <template #available-cell="{ row }">
                <span
                    class="text-sm font-semibold"
                    :class="row.original.available > 0 ? 'text-success' : 'text-muted'"
                    >{{ row.original.available }}</span
                >
            </template>
            <template #actions-cell="{ row }">
                <div v-if="canCreate && row.original.locationId" class="flex justify-end gap-1">
                    <UButton
                        label="Reserve"
                        size="xs"
                        color="neutral"
                        variant="outline"
                        @click="openAction('reserve', row.original)"
                    />
                    <UButton
                        label="Release"
                        size="xs"
                        color="neutral"
                        variant="ghost"
                        @click="openAction('release', row.original)"
                    />
                </div>
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-package" message="No stock for this selection" />
            </template>
        </AppListTable>

        <div
            v-else
            class="text-muted border-default rounded-lg border border-dashed py-12 text-center text-sm"
        >
            Select a product and warehouse to view stock levels.
        </div>

        <!-- Reserve / release modal -->
        <AppConfirmModal
            v-model:open="actionOpen"
            :title="actionMode === 'reserve' ? 'Reserve stock' : 'Release stock'"
            :confirm-label="actionMode === 'reserve' ? 'Reserve' : 'Release'"
            :loading="submitting"
            :confirm-disabled="actionQty < 1"
            @confirm="submitAction"
        >
            <form class="space-y-3" @submit.prevent="submitAction">
                <p class="text-muted text-sm">
                    {{ actionMode === 'reserve' ? 'Reserve' : 'Release' }} stock at
                    <strong class="text-highlighted">{{
                        locationName(actionRow?.locationId)
                    }}</strong
                    >.
                </p>
                <UFormField label="Quantity" required :error="errors.qty">
                    <UInput v-model.number="actionQty" type="number" min="1" class="w-full" />
                </UFormField>
            </form>
        </AppConfirmModal>
    </div>
</template>
