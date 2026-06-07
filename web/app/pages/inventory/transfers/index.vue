<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { TransferOrderResponse } from '~/types/inventory'
import { TRANSFER_STATUS_COLOR, TRANSFER_STATUS_LABEL } from '~/types/inventory'
import { required } from '~/utils/validators'

definePageMeta({ title: 'Transfers' })
useHead({ title: 'Transfers — Synoptic' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const {
    productOptions,
    warehouseOptions,
    productName,
    locationLabel,
    locationOptionsForWarehouse,
    loadProducts,
    loadAllLocations,
    ensureLocations,
} = useInventoryLookups()

const canManage = can('inventory.transfers.manage')
const {
    submitting: saving,
    errors,
    run: runCreate,
    validate: validateCreate,
    clearErrors: clearCreateErrors,
} = useFormSubmit({ failureTitle: 'Failed to create transfer' })

const {
    data: transfers,
    pending,
    refresh,
} = await useAsyncData<TransferOrderResponse[]>(
    'inventory-transfers',
    () => api<TransferOrderResponse[]>('/api/inventory/transfers'),
    { default: () => [] }
)

const columns: TableColumn<TransferOrderResponse>[] = [
    { accessorKey: 'productId', header: 'Product' },
    { id: 'from', header: 'From' },
    { id: 'to', header: 'To' },
    { accessorKey: 'quantity', header: 'Qty' },
    { accessorKey: 'status', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
]
if (canManage) {
    columns.push({ id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } })
}

// ── Lifecycle (dispatch / receive / cancel) ─────────────────────────────────
const acting = ref(false)

const PAST_TENSE: Record<string, string> = {
    dispatch: 'dispatched',
    receive: 'received',
    cancel: 'cancelled',
}

async function runLifecycle(action: 'dispatch' | 'receive' | 'cancel', t: TransferOrderResponse) {
    acting.value = true
    try {
        await api(`/api/inventory/transfers/${t.id}/${action}`, { method: 'POST' })
        toast.add({ title: `Transfer ${PAST_TENSE[action]}`, color: 'success' })
        cancelOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: `Failed to ${action}`, description: e?.data?.message, color: 'error' })
    } finally {
        acting.value = false
    }
}

const cancelOpen = ref(false)
const cancelTarget = shallowRef<TransferOrderResponse | null>(null)
function promptCancel(t: TransferOrderResponse) {
    cancelTarget.value = t
    cancelOpen.value = true
}

function rowActions(t: TransferOrderResponse): DropdownMenuItem[][] {
    if (t.status === 'PENDING') {
        return [
            [
                {
                    label: 'Dispatch',
                    icon: 'i-tabler-truck-delivery',
                    onSelect: () => runLifecycle('dispatch', t),
                },
            ],
            [
                {
                    label: 'Cancel',
                    icon: 'i-tabler-ban',
                    color: 'error',
                    onSelect: () => promptCancel(t),
                },
            ],
        ]
    }
    if (t.status === 'IN_TRANSIT') {
        return [
            [
                {
                    label: 'Receive',
                    icon: 'i-tabler-package-import',
                    onSelect: () => runLifecycle('receive', t),
                },
            ],
        ]
    }
    return []
}

// ── Create ──────────────────────────────────────────────────────────────────
const createOpen = ref(false)
const form = reactive({
    fromWarehouseId: '',
    fromLocationId: '',
    toWarehouseId: '',
    toLocationId: '',
    productId: '',
    quantity: 1,
    notes: '',
})

const fromLocationOptions = computed(() => locationOptionsForWarehouse(form.fromWarehouseId))
const toLocationOptions = computed(() => locationOptionsForWarehouse(form.toWarehouseId))

watch(
    () => form.fromWarehouseId,
    async (id) => {
        form.fromLocationId = ''
        if (id) await ensureLocations(id)
    }
)
watch(
    () => form.toWarehouseId,
    async (id) => {
        form.toLocationId = ''
        if (id) await ensureLocations(id)
    }
)

const createValid = computed(
    () =>
        !!form.fromLocationId &&
        !!form.toLocationId &&
        form.fromLocationId !== form.toLocationId &&
        !!form.productId &&
        form.quantity >= 1
)

function openCreate() {
    clearCreateErrors()
    Object.assign(form, {
        fromWarehouseId: '',
        fromLocationId: '',
        toWarehouseId: '',
        toLocationId: '',
        productId: '',
        quantity: 1,
        notes: '',
    })
    createOpen.value = true
}

const differentLocation = (v: unknown) =>
    v && v === form.fromLocationId ? 'Destination must differ from source' : undefined
const qtyAtLeastOne = (v: unknown) =>
    typeof v !== 'number' || Number.isNaN(v) || v < 1 ? 'Enter a quantity of at least 1' : undefined

function submitCreate() {
    runCreate({
        validate: () =>
            validateCreate(form, {
                productId: [required('Select a product')],
                fromLocationId: [required('Select a source location')],
                toLocationId: [required('Select a destination location'), differentLocation],
                quantity: [qtyAtLeastOne],
            }),
        call: () =>
            api('/api/inventory/transfers', {
                method: 'POST',
                body: {
                    fromLocationId: form.fromLocationId,
                    toLocationId: form.toLocationId,
                    productId: form.productId,
                    quantity: form.quantity,
                    notes: form.notes || undefined,
                },
            }),
        fieldHints: ['productId', 'fromLocationId', 'toLocationId', 'quantity'],
        onSuccess: () => {
            toast.add({ title: 'Transfer created', color: 'success' })
            createOpen.value = false
            refresh()
        },
    })
}

onMounted(() => {
    loadProducts()
    loadAllLocations()
})
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Transfers"
            :subtitle="`${transfers?.length ?? 0} transfer order${(transfers?.length ?? 0) === 1 ? '' : 's'}`"
        >
            <template #actions>
                <UButton
                    v-if="can('inventory.transfers.create')"
                    icon="i-tabler-plus"
                    label="New Transfer"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="transfers ?? []" :columns="columns" :loading="pending">
            <template #productId-cell="{ row }">
                <NuxtLink
                    :to="`/products/${row.original.productId}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ productName(row.original.productId) }}
                </NuxtLink>
            </template>
            <template #from-cell="{ row }">
                <span class="text-sm">{{ locationLabel(row.original.fromLocationId) }}</span>
            </template>
            <template #to-cell="{ row }">
                <span class="text-sm">{{ locationLabel(row.original.toLocationId) }}</span>
            </template>
            <template #quantity-cell="{ row }">
                <span class="text-highlighted text-sm font-semibold">{{
                    row.original.quantity
                }}</span>
            </template>
            <template #status-cell="{ row }">
                <UBadge
                    :label="TRANSFER_STATUS_LABEL[row.original.status]"
                    :color="TRANSFER_STATUS_COLOR[row.original.status]"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions
                    v-if="rowActions(row.original).length"
                    :items="rowActions(row.original)"
                />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-arrows-exchange" message="No transfer orders yet" />
            </template>
        </AppListTable>

        <!-- Create transfer modal -->
        <AppConfirmModal
            v-model:open="createOpen"
            title="New Transfer"
            confirm-label="Create"
            :loading="saving"
            :confirm-disabled="!createValid"
            width-class="sm:max-w-2xl"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Product" required :error="errors.productId">
                    <USelect
                        v-model="form.productId"
                        :items="productOptions"
                        placeholder="Select product"
                        class="w-full"
                    />
                </UFormField>
                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-3">
                        <p class="text-muted text-xs font-semibold uppercase">From</p>
                        <UFormField label="Warehouse" required>
                            <USelect
                                v-model="form.fromWarehouseId"
                                :items="warehouseOptions"
                                placeholder="Select warehouse"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Location" required :error="errors.fromLocationId">
                            <USelect
                                v-model="form.fromLocationId"
                                :items="fromLocationOptions"
                                :disabled="!form.fromWarehouseId"
                                placeholder="Select location"
                                class="w-full"
                            />
                        </UFormField>
                    </div>
                    <div class="space-y-3">
                        <p class="text-muted text-xs font-semibold uppercase">To</p>
                        <UFormField label="Warehouse" required>
                            <USelect
                                v-model="form.toWarehouseId"
                                :items="warehouseOptions"
                                placeholder="Select warehouse"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Location" required :error="errors.toLocationId">
                            <USelect
                                v-model="form.toLocationId"
                                :items="toLocationOptions"
                                :disabled="!form.toWarehouseId"
                                placeholder="Select location"
                                class="w-full"
                            />
                        </UFormField>
                    </div>
                </div>
                <p
                    v-if="
                        form.fromLocationId &&
                        form.toLocationId &&
                        form.fromLocationId === form.toLocationId
                    "
                    class="text-error text-xs"
                >
                    Source and destination locations must be different.
                </p>
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Quantity" required :error="errors.quantity">
                        <UInput
                            v-model.number="form.quantity"
                            type="number"
                            min="1"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <UFormField label="Notes" hint="Optional">
                    <UTextarea v-model="form.notes" :rows="2" class="w-full" />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Cancel transfer modal -->
        <AppConfirmModal
            v-model:open="cancelOpen"
            title="Cancel transfer"
            confirm-label="Cancel transfer"
            confirm-color="error"
            :loading="acting"
            @confirm="cancelTarget && runLifecycle('cancel', cancelTarget)"
        >
            <p class="text-muted text-sm">
                Cancel this transfer of
                <strong class="text-highlighted">{{
                    cancelTarget ? productName(cancelTarget.productId) : ''
                }}</strong>
                ? Only pending transfers can be cancelled.
            </p>
        </AppConfirmModal>
    </div>
</template>
