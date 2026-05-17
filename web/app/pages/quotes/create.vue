<script setup lang="ts">
import type { LeadResponse } from '~/types/leads'
import type { ProductResponse } from '~/types/inventory'
import type { LeadProductResponse } from '~/types/quotes'

definePageMeta({ title: 'New Quote' })
useHead({ title: 'New Quote — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { formatCurrency } = useFormatters()

const { data: leads } = await useAsyncData<LeadResponse[]>('quotes-leads', () =>
    api<LeadResponse[]>('/api/leads')
)
const { data: products } = await useAsyncData<ProductResponse[]>('quotes-products', () =>
    api<ProductResponse[]>('/api/products')
)

const leadOptions = computed(() => leads.value?.map((l) => ({ label: l.title, value: l.id })) ?? [])
const productOptions = computed(
    () => products.value?.map((p) => ({ label: p.name, value: p.id })) ?? []
)

const saving = ref(false)
const loadingLeadProducts = ref(false)

async function onLeadSelect(leadId: string) {
    if (!leadId) return
    loadingLeadProducts.value = true
    try {
        const leadProducts = await api<LeadProductResponse[]>(`/api/quotes/lead-products/${leadId}`)
        if (leadProducts.length > 0) {
            items.value = leadProducts.map((lp) => ({
                productId: lp.productId,
                quantity: lp.quantity,
                unitPrice: lp.unitPrice,
                discount: 0,
            }))
            toast.add({
                title: `Pre-filled ${leadProducts.length} product${leadProducts.length > 1 ? 's' : ''} from lead`,
                color: 'info',
            })
        }
    } catch {
        // Non-critical — silently skip pre-fill on error
    } finally {
        loadingLeadProducts.value = false
    }
}
const form = reactive({
    leadId: (route.query.leadId as string) ?? '',
    title: '',
    discount: 0,
    tax: 0,
    terms: '',
    expiredAt: '',
})

interface LineItem {
    productId: string
    quantity: number
    unitPrice: number
    discount: number
}

const items = ref<LineItem[]>([])

function addItem() {
    items.value.push({ productId: '', quantity: 1, unitPrice: 0, discount: 0 })
}

function removeItem(i: number) {
    items.value.splice(i, 1)
}

function onProductSelect(i: number, productId: string) {
    const product = products.value?.find((p) => p.id === productId)
    if (product) items.value[i]!.unitPrice = product.price ?? 0
}

function lineTotal(item: LineItem) {
    return item.quantity * item.unitPrice * (1 - item.discount / 100)
}

const subTotal = computed(() => items.value.reduce((s, it) => s + lineTotal(it), 0))
const grandTotal = computed(() => subTotal.value * (1 - form.discount / 100) * (1 + form.tax / 100))

async function submit() {
    saving.value = true
    try {
        const quote = await api<{ id: string }>('/api/quotes', {
            method: 'POST',
            body: {
                leadId: form.leadId,
                title: form.title,
                discount: form.discount || undefined,
                tax: form.tax || undefined,
                terms: form.terms || undefined,
                expiredAt: form.expiredAt || undefined,
                items: items.value
                    .filter((it) => it.productId)
                    .map((it) => ({
                        productId: it.productId,
                        quantity: it.quantity,
                        unitPrice: it.unitPrice,
                        discount: it.discount || undefined,
                    })),
            },
        })
        toast.add({ title: 'Quote created', color: 'success' })
        router.push(`/quotes/${quote.id}`)
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}
</script>

<template>
    <div class="mx-auto max-w-3xl space-y-6">
        <div class="flex items-center gap-3">
            <UButton icon="i-tabler-arrow-left" color="neutral" variant="ghost" to="/quotes" />
            <h2 class="text-highlighted text-xl font-semibold">New Quote</h2>
        </div>

        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Lead" required>
                        <USelect
                            v-model="form.leadId"
                            :items="leadOptions"
                            placeholder="Select lead"
                            class="w-full"
                            @update:model-value="onLeadSelect"
                        />
                    </UFormField>
                    <UFormField label="Title" required>
                        <UInput
                            v-model="form.title"
                            placeholder="e.g. Proposal Q1"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <div class="grid grid-cols-3 gap-4">
                    <UFormField label="Discount (%)">
                        <UInput
                            v-model.number="form.discount"
                            type="number"
                            min="0"
                            max="100"
                            step="0.1"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Tax (%)">
                        <UInput
                            v-model.number="form.tax"
                            type="number"
                            min="0"
                            step="0.1"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Expires">
                        <UInput v-model="form.expiredAt" type="date" class="w-full" />
                    </UFormField>
                </div>
                <UFormField label="Terms">
                    <UTextarea
                        v-model="form.terms"
                        :rows="2"
                        placeholder="Payment terms, conditions…"
                        class="w-full"
                    />
                </UFormField>

                <!-- Line items -->
                <div class="space-y-2">
                    <div class="flex items-center justify-between">
                        <p class="text-highlighted text-sm font-semibold">Line Items</p>
                        <UButton
                            icon="i-tabler-plus"
                            size="xs"
                            color="neutral"
                            variant="outline"
                            label="Add item"
                            @click="addItem"
                        />
                    </div>
                    <div
                        v-if="items.length === 0"
                        class="text-muted border-default rounded-lg border py-4 text-center text-sm"
                    >
                        No items added yet
                    </div>
                    <div
                        v-for="(item, i) in items"
                        :key="i"
                        class="border-default grid grid-cols-12 items-end gap-2 rounded-lg border p-3"
                    >
                        <UFormField label="Product" class="col-span-4">
                            <USelect
                                v-model="item.productId"
                                :items="productOptions"
                                placeholder="Select product"
                                class="w-full"
                                @update:model-value="onProductSelect(i, $event as string)"
                            />
                        </UFormField>
                        <UFormField label="Qty" class="col-span-2">
                            <UInput
                                v-model.number="item.quantity"
                                type="number"
                                min="1"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Unit Price" class="col-span-3">
                            <UInput
                                v-model.number="item.unitPrice"
                                type="number"
                                step="0.01"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Disc %" class="col-span-2">
                            <UInput
                                v-model.number="item.discount"
                                type="number"
                                min="0"
                                max="100"
                                class="w-full"
                            />
                        </UFormField>
                        <div class="col-span-1 pb-1 text-right">
                            <UButton
                                icon="i-tabler-trash"
                                color="error"
                                variant="ghost"
                                size="xs"
                                @click="removeItem(i)"
                            />
                        </div>
                    </div>
                </div>

                <!-- Totals -->
                <div v-if="items.length > 0" class="border-default rounded-lg border p-3">
                    <div class="flex justify-between text-sm">
                        <span class="text-muted">Subtotal</span>
                        <span class="text-highlighted">{{ formatCurrency(subTotal) }}</span>
                    </div>
                    <div v-if="form.discount" class="flex justify-between text-sm">
                        <span class="text-muted">Discount ({{ form.discount }}%)</span>
                        <span class="text-error"
                            >-{{ formatCurrency((subTotal * form.discount) / 100) }}</span
                        >
                    </div>
                    <div v-if="form.tax" class="flex justify-between text-sm">
                        <span class="text-muted">Tax ({{ form.tax }}%)</span>
                        <span class="text-highlighted"
                            >+{{
                                formatCurrency(
                                    (subTotal * (1 - form.discount / 100) * form.tax) / 100
                                )
                            }}</span
                        >
                    </div>
                    <div class="border-default mt-2 flex justify-between border-t pt-2">
                        <span class="text-highlighted font-semibold">Total</span>
                        <span class="text-highlighted font-bold">{{
                            formatCurrency(grandTotal)
                        }}</span>
                    </div>
                </div>

                <div class="flex justify-end gap-2 pt-2">
                    <UButton color="neutral" variant="outline" label="Cancel" to="/quotes" />
                    <UButton
                        label="Create Quote"
                        :loading="saving"
                        :disabled="!form.leadId || !form.title"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </div>
</template>
