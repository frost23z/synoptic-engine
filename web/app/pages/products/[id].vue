<script setup lang="ts">
import type { ProductResponse } from '~/types/inventory'

definePageMeta({ title: 'Product' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const router = useRouter()
const route = useRoute()
const { formatCurrency, formatDate } = useFormatters()
const id = route.params.id as string

const {
    data: product,
    pending: productPending,
    refresh,
} = await useAsyncData<ProductResponse>(`product-${id}`, () =>
    api<ProductResponse>(`/api/products/${id}`)
)

const pageTitle = computed(() =>
    product.value?.name ? `${product.value.name} — Synoptic` : 'Product — Synoptic'
)
useHead({ title: pageTitle })

// ── Edit ──────────────────────────────────────────────────────────────────
const editing = ref(false)
const saving = ref(false)
const editForm = reactive({ name: '', description: '', price: 0, sku: '', active: true })

function openEdit() {
    if (!product.value) return
    Object.assign(editForm, {
        name: product.value.name,
        description: product.value.description ?? '',
        price: product.value.price ?? 0,
        sku: product.value.sku ?? '',
        active: product.value.active,
    })
    editing.value = true
}

async function submitEdit() {
    saving.value = true
    try {
        await api(`/api/products/${id}`, {
            method: 'PUT',
            body: {
                name: editForm.name,
                description: editForm.description || undefined,
                price: editForm.price,
                sku: editForm.sku || undefined,
                active: editForm.active,
            },
        })
        toast.add({ title: 'Saved', color: 'success' })
        editing.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const deleting = ref(false)

async function confirmDelete() {
    deleting.value = true
    try {
        await api(`/api/products/${id}`, { method: 'DELETE' })
        toast.add({ title: 'Product deleted', color: 'success' })
        router.push('/products')
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}
</script>

<template>
    <div v-if="product" class="space-y-6">
        <!-- Header -->
        <div class="flex items-start justify-between">
            <div class="flex items-center gap-3">
                <UButton
                    icon="i-tabler-arrow-left"
                    color="neutral"
                    variant="ghost"
                    to="/products"
                />
                <div>
                    <h2 class="text-highlighted text-xl font-semibold">{{ product.name }}</h2>
                    <p class="text-muted text-sm">
                        {{ product.sku ? `SKU: ${product.sku}` : 'No SKU' }}
                    </p>
                </div>
            </div>
            <div class="flex gap-2">
                <UButton
                    v-if="can('products.edit')"
                    icon="i-tabler-pencil"
                    label="Edit"
                    color="neutral"
                    variant="outline"
                    @click="openEdit"
                />
                <UButton
                    v-if="can('products.delete')"
                    icon="i-tabler-trash"
                    color="error"
                    variant="outline"
                    @click="deleteOpen = true"
                />
            </div>
        </div>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div class="space-y-4 lg:col-span-2">
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Details</p></template
                    >
                    <dl class="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                        <div>
                            <dt class="text-muted">Price</dt>
                            <dd class="text-highlighted mt-0.5 text-lg font-semibold">
                                {{ formatCurrency(product.price ?? 0) }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Status</dt>
                            <dd class="mt-0.5">
                                <UBadge
                                    :label="product.active ? 'Active' : 'Inactive'"
                                    :color="product.active ? 'success' : 'neutral'"
                                    variant="soft"
                                    size="sm"
                                />
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">SKU</dt>
                            <dd class="text-highlighted mt-0.5">{{ product.sku ?? '—' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted">Created</dt>
                            <dd class="text-muted mt-0.5">{{ formatDate(product.createdAt) }}</dd>
                        </div>
                        <div v-if="product.description" class="col-span-2">
                            <dt class="text-muted">Description</dt>
                            <dd class="text-highlighted mt-0.5">{{ product.description }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <!-- Edit modal -->
        <UModal v-model:open="editing">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Edit Product</p></template
                    >
                    <form class="space-y-3" @submit.prevent="submitEdit">
                        <UFormField label="Name" required>
                            <UInput v-model="editForm.name" class="w-full" />
                        </UFormField>
                        <UFormField label="Description">
                            <UTextarea v-model="editForm.description" :rows="3" class="w-full" />
                        </UFormField>
                        <div class="grid grid-cols-2 gap-3">
                            <UFormField label="Price">
                                <UInput
                                    v-model.number="editForm.price"
                                    type="number"
                                    step="0.01"
                                    class="w-full"
                                />
                            </UFormField>
                            <UFormField label="SKU">
                                <UInput v-model="editForm.sku" class="w-full" />
                            </UFormField>
                        </div>
                        <USwitch v-model="editForm.active" label="Active" />
                    </form>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="editing = false"
                            />
                            <UButton
                                label="Save"
                                :loading="saving"
                                :disabled="!editForm.name"
                                @click="submitEdit"
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
                        ><p class="text-highlighted font-semibold">Delete Product</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ product.name }}</strong
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

    <div v-else-if="productPending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-32 w-full" />
    </div>
</template>
