<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { QuoteResponse, QuotesPage } from '~/types/quotes'
import { QUOTE_STATUS_COLOR, QUOTE_STATUS_LABEL } from '~/types/quotes'

definePageMeta({ title: 'Quotes' })
useHead({ title: 'Quotes — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatCurrency, formatDate } = useFormatters()
const { can } = usePermissions()

const page = ref(1)
const PAGE_SIZE = 20
const statusFilter = ref<string | undefined>(undefined)

const statusOptions = [
    { label: 'All', value: undefined },
    { label: 'Draft', value: 'draft' },
    { label: 'Sent', value: 'sent' },
    { label: 'Accepted', value: 'accepted' },
    { label: 'Declined', value: 'declined' },
]

const queryKey = computed(() => ['quotes', page.value, statusFilter.value])

const {
    data: quotesPage,
    pending,
    refresh,
} = await useAsyncData<QuotesPage>(
    () => queryKey.value.join('-'),
    () => {
        const params: Record<string, string | number> = { page: page.value - 1, size: PAGE_SIZE }
        if (statusFilter.value) params.status = statusFilter.value.toUpperCase()
        return api<QuotesPage>('/api/quotes', { params })
    },
    { watch: [queryKey] }
)

const quotes = computed(() => quotesPage.value?.content ?? [])
const total = computed(() => quotesPage.value?.totalElements ?? 0)

// ── Status update ──────────────────────────────────────────────────────
const statusUpdateOpen = ref(false)
const quoteForStatus = ref<QuoteResponse | null>(null)
const newStatus = ref<string>('')
const updatingStatus = ref(false)

const STATUS_OPTIONS_FOR_UPDATE = [
    { label: 'Draft', value: 'DRAFT' },
    { label: 'Sent', value: 'SENT' },
    { label: 'Accepted', value: 'ACCEPTED' },
    { label: 'Declined', value: 'DECLINED' },
]

function openStatusUpdate(quote: QuoteResponse) {
    quoteForStatus.value = quote
    newStatus.value = quote.status.toUpperCase()
    statusUpdateOpen.value = true
}

async function confirmStatusUpdate() {
    if (!quoteForStatus.value) return
    updatingStatus.value = true
    try {
        await api(`/api/quotes/${quoteForStatus.value.id}/status`, {
            method: 'PATCH',
            body: { status: newStatus.value },
        })
        toast.add({ title: 'Status updated', color: 'success' })
        statusUpdateOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to update status', color: 'error' })
    } finally {
        updatingStatus.value = false
    }
}

// ── Mass delete ───────────────────────────────────────────────────────────
const { selected, isSelected, toggle, selectAll, clearAll, hasSelection, count } = useMassSelect()
const massDeleting = ref(false)

async function massDelete() {
    if (!hasSelection.value) return
    massDeleting.value = true
    try {
        await api('/api/quotes/mass-destroy', {
            method: 'POST',
            body: { ids: selected.value },
        })
        toast.add({ title: `${count.value} quotes deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

// ── Delete ──────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<QuoteResponse | null>(null)
const deleting = ref(false)

function openDeleteModal(q: QuoteResponse) {
    toDelete.value = q
    deleteOpen.value = true
}

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/quotes/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Quote deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Table columns ────────────────────────────────────────────────────────
const columns: TableColumn<QuoteResponse>[] = [
    { id: 'select', header: '', meta: { class: { th: 'w-8', td: 'w-8' } } },
    { accessorKey: 'title', header: 'Title' },
    { accessorKey: 'status', header: 'Status' },
    { accessorKey: 'grandTotal', header: 'Total' },
    { accessorKey: 'expiredAt', header: 'Expires' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(q: QuoteResponse) {
    return [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                click: () => router.push(`/quotes/${q.id}`),
            },
            {
                label: 'Update status',
                icon: 'i-tabler-tag',
                click: () => openStatusUpdate(q),
            },
        ],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => openDeleteModal(q),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <!-- Page header -->
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Quotes</h2>
                <p class="text-muted text-sm">{{ total.toLocaleString() }} total</p>
            </div>
            <UButton
                v-if="can('quotes.create')"
                icon="i-tabler-plus"
                label="New Quote"
                to="/quotes/create"
            />
        </div>

        <!-- Filters -->
        <div class="flex items-center gap-3">
            <USelect
                v-model="statusFilter"
                :items="statusOptions"
                placeholder="Status"
                class="w-36"
            />
        </div>

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
            <UTable :data="quotes" :columns="columns" :loading="pending" sticky>
                <template #select-header>
                    <UCheckbox
                        :checked="quotes.length > 0 && selected.length === quotes.length"
                        :indeterminate="selected.length > 0 && selected.length < quotes.length"
                        @change="
                            quotes.length === selected.length
                                ? clearAll()
                                : selectAll(quotes.map((i) => i.id))
                        "
                    />
                </template>
                <template #select-cell="{ row }">
                    <UCheckbox
                        :checked="isSelected(row.original.id)"
                        @change="toggle(row.original.id)"
                    />
                </template>

                <!-- Title -->
                <template #title-cell="{ row }">
                    <NuxtLink
                        :to="`/quotes/${row.original.id}`"
                        class="text-primary font-medium hover:underline"
                    >
                        {{ row.original.title }}
                    </NuxtLink>
                </template>

                <!-- Status badge -->
                <template #status-cell="{ row }">
                    <UBadge
                        :label="QUOTE_STATUS_LABEL[row.original.status]"
                        :color="QUOTE_STATUS_COLOR[row.original.status]"
                        variant="soft"
                        size="sm"
                    />
                </template>

                <!-- Grand total -->
                <template #grandTotal-cell="{ row }">
                    <span class="font-semibold">
                        {{ formatCurrency(row.original.grandTotal) }}
                    </span>
                </template>

                <!-- Expiry -->
                <template #expiredAt-cell="{ row }">
                    <span
                        class="text-sm"
                        :class="
                            row.original.expiredAt && new Date(row.original.expiredAt) < new Date()
                                ? 'text-error font-medium'
                                : 'text-muted'
                        "
                    >
                        {{ formatDate(row.original.expiredAt) }}
                    </span>
                </template>

                <!-- Created -->
                <template #createdAt-cell="{ row }">
                    <span class="text-muted text-sm">
                        {{ formatDate(row.original.createdAt) }}
                    </span>
                </template>

                <!-- Actions -->
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
                        <UIcon
                            name="i-tabler-file-invoice-off"
                            class="text-muted mx-auto size-10"
                        />
                        <p class="text-muted text-sm">No quotes found</p>
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

        <!-- Status update modal -->
        <UModal v-model:open="statusUpdateOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Update Quote Status</p>
                    </template>
                    <USelect
                        v-model="newStatus"
                        :items="STATUS_OPTIONS_FOR_UPDATE"
                        label="New status"
                        class="w-full"
                    />
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="statusUpdateOpen = false"
                            />
                            <UButton
                                label="Update"
                                :loading="updatingStatus"
                                @click="confirmStatusUpdate"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete modal -->
        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Delete Quote</p>
                    </template>
                    <p class="text-muted text-sm">
                        Delete
                        <strong class="text-highlighted">{{ toDelete?.title }}</strong
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
