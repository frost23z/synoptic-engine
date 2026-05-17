<script setup lang="ts">
import type { QuoteResponse, QuoteStatus } from '~/types/quotes'
import type { ProductResponse } from '~/types/inventory'
import { QUOTE_STATUS_COLOR, QUOTE_STATUS_LABEL } from '~/types/quotes'

definePageMeta({ title: 'Quote' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const router = useRouter()
const route = useRoute()
const { formatCurrency, formatDate } = useFormatters()
const id = route.params.id as string

const {
    data: quote,
    pending: quotePending,
    refresh,
} = await useAsyncData<QuoteResponse>(`quote-${id}`, () => api<QuoteResponse>(`/api/quotes/${id}`))
const pageTitle = computed(() =>
    quote.value?.title ? `${quote.value.title} — Synoptic` : 'Quote — Synoptic'
)
useHead({ title: pageTitle })

const { data: products } = await useAsyncData<ProductResponse[]>('quote-products', () =>
    api<ProductResponse[]>('/api/products')
)
const productMap = computed(() => Object.fromEntries((products.value ?? []).map((p) => [p.id, p])))

// ── Status change ─────────────────────────────────────────────────────────
const STATUS_OPTIONS: { label: string; value: string }[] = [
    { label: 'Draft', value: 'DRAFT' },
    { label: 'Sent', value: 'SENT' },
    { label: 'Accepted', value: 'ACCEPTED' },
    { label: 'Declined', value: 'DECLINED' },
]

const updatingStatus = ref(false)

async function changeStatus(status: string) {
    updatingStatus.value = true
    try {
        await api(`/api/quotes/${id}/status`, { method: 'PATCH', body: { status } })
        toast.add({ title: 'Status updated', color: 'success' })
        refresh()
    } catch {
        toast.add({ title: 'Failed to update status', color: 'error' })
    } finally {
        updatingStatus.value = false
    }
}

// ── Send mail ─────────────────────────────────────────────────────────────
const sendMailOpen = ref(false)
const sending = ref(false)
const mailForm = reactive({ to: '', subject: '', message: '' })

function openSendMail() {
    Object.assign(mailForm, { to: '', subject: `Quote: ${quote.value?.title ?? ''}`, message: '' })
    sendMailOpen.value = true
}

async function submitSendMail() {
    sending.value = true
    try {
        await api(`/api/quotes/${id}/send-mail`, {
            method: 'POST',
            body: { to: mailForm.to, subject: mailForm.subject, message: mailForm.message },
        })
        toast.add({ title: 'Quote sent', color: 'success' })
        sendMailOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to send', description: e?.data?.message, color: 'error' })
    } finally {
        sending.value = false
    }
}

// ── PDF download ──────────────────────────────────────────────────────────
const { downloadBlob } = useDownload()
const downloadingPdf = ref(false)

async function downloadPdf() {
    if (!quote.value) return
    downloadingPdf.value = true
    try {
        await downloadBlob(`/api/quotes/${id}/print`, `quote-${quote.value.title}.pdf`)
    } catch {
        toast.add({ title: 'Failed to download PDF', color: 'error' })
    } finally {
        downloadingPdf.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const deleting = ref(false)

async function confirmDelete() {
    deleting.value = true
    try {
        await api(`/api/quotes/${id}`, { method: 'DELETE' })
        toast.add({ title: 'Quote deleted', color: 'success' })
        router.push('/quotes')
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}
</script>

<template>
    <div v-if="quote" class="space-y-6">
        <!-- Header -->
        <div class="flex items-start justify-between">
            <div class="flex items-center gap-3">
                <UButton icon="i-tabler-arrow-left" color="neutral" variant="ghost" to="/quotes" />
                <div>
                    <h2 class="text-highlighted text-xl font-semibold">{{ quote.title }}</h2>
                    <div class="mt-0.5 flex items-center gap-2">
                        <UBadge
                            :label="QUOTE_STATUS_LABEL[quote.status as QuoteStatus] ?? quote.status"
                            :color="QUOTE_STATUS_COLOR[quote.status as QuoteStatus] ?? 'neutral'"
                            variant="soft"
                            size="sm"
                        />
                        <span class="text-muted text-sm">{{ formatDate(quote.createdAt) }}</span>
                    </div>
                </div>
            </div>
            <div class="flex flex-wrap gap-2">
                <UDropdownMenu
                    :items="[
                        STATUS_OPTIONS.filter((s) => s.value.toLowerCase() !== quote.status).map(
                            (s) => ({ label: s.label, click: () => changeStatus(s.value) })
                        ),
                    ]"
                >
                    <UButton
                        icon="i-tabler-refresh"
                        label="Change Status"
                        color="neutral"
                        variant="outline"
                        size="sm"
                        :loading="updatingStatus"
                    />
                </UDropdownMenu>
                <UButton
                    icon="i-tabler-send"
                    label="Send"
                    color="neutral"
                    variant="outline"
                    size="sm"
                    @click="openSendMail"
                />
                <UButton
                    icon="i-tabler-file-download"
                    label="Download PDF"
                    color="neutral"
                    variant="outline"
                    size="sm"
                    :loading="downloadingPdf"
                    @click="downloadPdf"
                />
                <UButton
                    v-if="can('quotes.delete')"
                    icon="i-tabler-trash"
                    color="error"
                    variant="outline"
                    size="sm"
                    @click="deleteOpen = true"
                />
            </div>
        </div>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <!-- Line items -->
            <div class="lg:col-span-2">
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Line Items</p></template
                    >
                    <div v-if="!quote.items?.length" class="text-muted py-6 text-center text-sm">
                        No items
                    </div>
                    <table v-else class="w-full text-sm">
                        <thead>
                            <tr class="border-default border-b">
                                <th class="text-muted pb-2 text-left font-medium">Product</th>
                                <th class="text-muted pb-2 text-right font-medium">Qty</th>
                                <th class="text-muted pb-2 text-right font-medium">Unit Price</th>
                                <th class="text-muted pb-2 text-right font-medium">Disc %</th>
                                <th class="text-muted pb-2 text-right font-medium">Total</th>
                            </tr>
                        </thead>
                        <tbody class="divide-default divide-y">
                            <tr v-for="item in quote.items" :key="item.id">
                                <td class="text-highlighted py-2.5">
                                    {{ productMap[item.productId]?.name ?? item.productId }}
                                </td>
                                <td class="text-default py-2.5 text-right">{{ item.quantity }}</td>
                                <td class="text-default py-2.5 text-right">
                                    {{ formatCurrency(item.unitPrice) }}
                                </td>
                                <td class="text-default py-2.5 text-right">
                                    {{ item.discount ? `${item.discount}%` : '—' }}
                                </td>
                                <td class="text-highlighted py-2.5 text-right font-medium">
                                    {{ formatCurrency(item.lineTotal) }}
                                </td>
                            </tr>
                        </tbody>
                    </table>
                    <div class="border-default mt-4 space-y-1.5 border-t pt-4">
                        <div class="flex justify-between text-sm">
                            <span class="text-muted">Subtotal</span>
                            <span class="text-highlighted">{{
                                formatCurrency(quote.subTotal)
                            }}</span>
                        </div>
                        <div v-if="quote.discount" class="flex justify-between text-sm">
                            <span class="text-muted">Discount ({{ quote.discount }}%)</span>
                            <span class="text-error"
                                >-{{
                                    formatCurrency((quote.subTotal * quote.discount) / 100)
                                }}</span
                            >
                        </div>
                        <div v-if="quote.tax" class="flex justify-between text-sm">
                            <span class="text-muted">Tax ({{ quote.tax }}%)</span>
                            <span
                                >+{{
                                    formatCurrency(
                                        quote.grandTotal -
                                            quote.subTotal * (1 - (quote.discount ?? 0) / 100)
                                    )
                                }}</span
                            >
                        </div>
                        <div
                            class="border-default flex justify-between border-t pt-2 font-semibold"
                        >
                            <span class="text-highlighted">Grand Total</span>
                            <span class="text-highlighted text-lg">{{
                                formatCurrency(quote.grandTotal)
                            }}</span>
                        </div>
                    </div>
                </UCard>
            </div>

            <!-- Sidebar -->
            <div class="space-y-4">
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Details</p></template
                    >
                    <dl class="space-y-3 text-sm">
                        <div>
                            <dt class="text-muted">Expires</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ quote.expiredAt ? formatDate(quote.expiredAt) : '—' }}
                            </dd>
                        </div>
                        <div v-if="quote.terms">
                            <dt class="text-muted">Terms</dt>
                            <dd class="text-highlighted mt-0.5 text-xs whitespace-pre-wrap">
                                {{ quote.terms }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Lead</dt>
                            <dd class="mt-0.5">
                                <NuxtLink
                                    :to="`/leads/${quote.leadId}`"
                                    class="text-primary text-sm hover:underline"
                                    >View lead</NuxtLink
                                >
                            </dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <!-- Send mail modal -->
        <UModal v-model:open="sendMailOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Send Quote</p></template
                    >
                    <form class="space-y-3" @submit.prevent="submitSendMail">
                        <UFormField label="To" required>
                            <UInput
                                v-model="mailForm.to"
                                type="email"
                                placeholder="recipient@example.com"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Subject">
                            <UInput v-model="mailForm.subject" class="w-full" />
                        </UFormField>
                        <UFormField label="Message">
                            <UTextarea v-model="mailForm.message" :rows="4" class="w-full" />
                        </UFormField>
                    </form>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="sendMailOpen = false"
                            />
                            <UButton
                                icon="i-tabler-send"
                                label="Send"
                                :loading="sending"
                                :disabled="!mailForm.to"
                                @click="submitSendMail"
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
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Quote</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ quote.title }}</strong
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

    <div v-else-if="quotePending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-32 w-full" />
    </div>
</template>
