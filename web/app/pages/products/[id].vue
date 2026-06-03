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
const shareOpen = ref(false)

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
const {
    open: deleteOpen,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<ProductResponse>({
    endpoint: (p) => `/api/products/${p.id}`,
    successMessage: 'Product deleted',
    onDeleted: () => {
        router.push('/products')
    },
})
</script>

<template>
    <AppDetailLayout
        v-if="product"
        to="/products"
        :title="product.name"
        :subtitle="product.sku ? `SKU: ${product.sku}` : 'No SKU'"
    >
        <template #actions>
            <UButton
                v-if="can('records.share')"
                icon="i-tabler-share"
                label="Share"
                color="neutral"
                variant="outline"
                @click="shareOpen = true"
            />
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
                @click="promptDelete(product)"
            />
        </template>

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
        <AppConfirmModal
            v-model:open="editing"
            title="Edit Product"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.name"
            @confirm="submitEdit"
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
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Product"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ product.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>

        <ShareRecordModal
            v-if="can('records.share')"
            v-model:open="shareOpen"
            resource-type="products"
            :resource-id="id"
            :resource-label="product.name"
        />
    </AppDetailLayout>

    <div v-else-if="productPending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-32 w-full" />
    </div>
</template>
