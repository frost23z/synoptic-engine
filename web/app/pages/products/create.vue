<script setup lang="ts">
definePageMeta({ title: 'New Product' })
useHead({ title: 'New Product — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()

const saving = ref(false)
const form = reactive({ name: '', description: '', price: 0, sku: '', active: true })

async function submit() {
    saving.value = true
    try {
        const product = await api<{ id: string }>('/api/products', {
            method: 'POST',
            body: {
                name: form.name,
                description: form.description || undefined,
                price: form.price || undefined,
                sku: form.sku || undefined,
                active: form.active,
            },
        })
        toast.add({ title: 'Product created', color: 'success' })
        router.push(`/products/${product.id}`)
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}
</script>

<template>
    <div class="mx-auto max-w-xl space-y-6">
        <div class="flex items-center gap-3">
            <UButton icon="i-tabler-arrow-left" color="neutral" variant="ghost" to="/products" />
            <h2 class="text-highlighted text-xl font-semibold">New Product</h2>
        </div>

        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <UFormField label="Name" required>
                    <UInput v-model="form.name" placeholder="e.g. CRM License" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UTextarea v-model="form.description" :rows="3" class="w-full" />
                </UFormField>
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Price">
                        <UInput
                            v-model.number="form.price"
                            type="number"
                            step="0.01"
                            placeholder="0.00"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="SKU">
                        <UInput v-model="form.sku" placeholder="SKU-001" class="w-full" />
                    </UFormField>
                </div>
                <USwitch v-model="form.active" label="Active" />
                <div class="flex justify-end gap-2 pt-2">
                    <UButton color="neutral" variant="outline" label="Cancel" to="/products" />
                    <UButton
                        label="Create Product"
                        :loading="saving"
                        :disabled="!form.name"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </div>
</template>
