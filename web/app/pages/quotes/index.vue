<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { QuoteResponse } from '~/types/quotes'
import { QUOTE_STATUS_COLOR, QUOTE_STATUS_LABEL } from '~/types/quotes'

definePageMeta({ title: 'Quotes' })
useHead({ title: 'Quotes — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatCurrency, formatDate } = useFormatters()
const { can } = usePermissions()

const statusFilter = ref<string | undefined>(undefined)
const statusOptions = [
    { label: 'All', value: undefined },
    { label: 'Draft', value: 'draft' },
    { label: 'Sent', value: 'sent' },
    { label: 'Accepted', value: 'accepted' },
    { label: 'Declined', value: 'declined' },
]

const {
    page,
    items: quotes,
    total,
    pending,
    refresh,
} = await usePaginatedList<QuoteResponse>('/api/quotes', {
    key: 'quotes',
    params: () => ({ status: statusFilter.value ? statusFilter.value.toUpperCase() : undefined }),
})

// ── Status update ──────────────────────────────────────────────────────
const statusUpdateOpen = ref(false)
const quoteForStatus = shallowRef<QuoteResponse | null>(null)
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

// ── Mass delete ──────────────────────────────────────────────────────────
const { selected, selectAll, clearAll, count } = useMassSelect()
const massDeleting = ref(false)
async function massDelete() {
    if (!selected.value.length) return
    massDeleting.value = true
    try {
        await api('/api/quotes/mass-destroy', { method: 'POST', body: { ids: selected.value } })
        toast.add({ title: `${count.value} quotes deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: quoteToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<QuoteResponse>({
    endpoint: (q) => `/api/quotes/${q.id}`,
    successMessage: 'Quote deleted',
    onDeleted: refresh,
})

const columns: TableColumn<QuoteResponse>[] = [
    { accessorKey: 'title', header: 'Title' },
    { accessorKey: 'status', header: 'Status' },
    { accessorKey: 'grandTotal', header: 'Total' },
    { accessorKey: 'expiredAt', header: 'Expires' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(q: QuoteResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [
            { label: 'View', icon: 'i-tabler-eye', onSelect: () => router.push(`/quotes/${q.id}`) },
            { label: 'Update status', icon: 'i-tabler-tag', onSelect: () => openStatusUpdate(q) },
        ],
    ]
    if (can('quotes.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(q),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Quotes" :subtitle="`${total.toLocaleString()} total`">
            <template #actions>
                <UButton
                    v-if="can('quotes.create')"
                    icon="i-tabler-plus"
                    label="New Quote"
                    to="/quotes/create"
                />
            </template>
        </AppPageHeader>

        <div class="flex items-center gap-3">
            <USelect
                v-model="statusFilter"
                :items="statusOptions"
                placeholder="Status"
                class="w-36"
            />
        </div>

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
            :rows="quotes"
            :columns="columns"
            :loading="pending"
            selectable
            :selected="selected"
            @update:selected="selectAll"
        >
            <template #title-cell="{ row }">
                <NuxtLink
                    :to="`/quotes/${row.original.id}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.title }}
                </NuxtLink>
            </template>
            <template #status-cell="{ row }">
                <UBadge
                    :label="QUOTE_STATUS_LABEL[row.original.status]"
                    :color="QUOTE_STATUS_COLOR[row.original.status]"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #grandTotal-cell="{ row }">
                <span class="font-semibold">{{ formatCurrency(row.original.grandTotal) }}</span>
            </template>
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
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-file-invoice-off" message="No quotes found" />
            </template>
        </AppListTable>

        <AppPagination v-model:page="page" :total="total" />

        <!-- Status update modal -->
        <AppConfirmModal
            v-model:open="statusUpdateOpen"
            title="Update Quote Status"
            confirm-label="Update"
            :loading="updatingStatus"
            @confirm="confirmStatusUpdate"
        >
            <USelect
                v-model="newStatus"
                :items="STATUS_OPTIONS_FOR_UPDATE"
                label="New status"
                class="w-full"
            />
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Quote"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ quoteToDelete?.title }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
