<script setup lang="ts">
import { required } from '~/utils/validators'

definePageMeta({ title: 'New Product' })
useHead({ title: 'New Product — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { submitting, errors, validate, run } = useFormSubmit({
    failureTitle: 'Failed to create product',
})

const form = reactive({ name: '', description: '', price: 0, sku: '', isActive: true, reorder: 0 })

function submit() {
    run({
        validate: () => validate(form, { name: [required('Name is required')] }),
        call: () =>
            api<{ id: string }>('/api/products', {
                method: 'POST',
                body: {
                    name: form.name,
                    description: form.description || undefined,
                    price: form.price,
                    sku: form.sku || undefined,
                    isActive: form.isActive,
                    reorderThreshold: form.reorder || undefined,
                },
            }),
        fieldHints: ['sku', 'name'],
        onSuccess: (product) => {
            toast.add({ title: 'Product created', color: 'success' })
            router.push(`/products/${product.id}`)
        },
    })
}
</script>

<template>
    <AppDetailLayout to="/products" title="New Product" root-class="mx-auto max-w-xl space-y-6">
        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <UFormField label="Name" required :error="errors.name">
                    <UInput v-model="form.name" placeholder="e.g. CRM License" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UTextarea v-model="form.description" :rows="3" class="w-full" />
                </UFormField>
                <div class="grid grid-cols-3 gap-4">
                    <UFormField label="Price">
                        <UInput
                            v-model.number="form.price"
                            type="number"
                            step="0.01"
                            placeholder="0.00"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="SKU" :error="errors.sku">
                        <UInput v-model="form.sku" placeholder="SKU-001" class="w-full" />
                    </UFormField>
                    <UFormField label="Reorder at" hint="Low-stock alert">
                        <UInput
                            v-model.number="form.reorder"
                            type="number"
                            min="0"
                            placeholder="0"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <USwitch v-model="form.isActive" label="Active" />
                <div class="flex justify-end gap-2 pt-2">
                    <UButton color="neutral" variant="outline" label="Cancel" to="/products" />
                    <UButton
                        label="Create Product"
                        :loading="submitting"
                        :disabled="!form.name"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </AppDetailLayout>
</template>
