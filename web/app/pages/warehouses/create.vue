<script setup lang="ts">
import type { WarehouseResponse } from '~/types/inventory'

definePageMeta({ title: 'New Warehouse' })
useHead({ title: 'New Warehouse — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()

const saving = ref(false)
const form = reactive({
    name: '',
    description: '',
    contactName: '',
    contactEmail: '',
    contactPhone: '',
    contactAddress: '',
})

async function submit() {
    if (!form.name.trim()) return
    saving.value = true
    try {
        const warehouse = await api<WarehouseResponse>('/api/warehouses', {
            method: 'POST',
            body: {
                name: form.name,
                description: form.description || undefined,
                contactName: form.contactName || undefined,
                contactEmail: form.contactEmail || undefined,
                contactPhone: form.contactPhone || undefined,
                contactAddress: form.contactAddress || undefined,
            },
        })
        toast.add({ title: 'Warehouse created', color: 'success' })
        router.push(`/warehouses/${warehouse.id}`)
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}
</script>

<template>
    <AppDetailLayout to="/warehouses" title="New Warehouse" root-class="mx-auto max-w-xl space-y-6">
        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <UFormField label="Name" required>
                    <UInput
                        v-model="form.name"
                        placeholder="e.g. Main Distribution Center"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UTextarea v-model="form.description" :rows="3" class="w-full" />
                </UFormField>
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Contact name">
                        <UInput v-model="form.contactName" placeholder="Jane Doe" class="w-full" />
                    </UFormField>
                    <UFormField label="Contact email">
                        <UInput
                            v-model="form.contactEmail"
                            type="email"
                            placeholder="ops@example.com"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Contact phone">
                        <UInput
                            v-model="form.contactPhone"
                            placeholder="+1 555 0100"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Contact address">
                        <UInput
                            v-model="form.contactAddress"
                            placeholder="123 Main St"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <div class="flex justify-end gap-2 pt-2">
                    <UButton color="neutral" variant="outline" label="Cancel" to="/warehouses" />
                    <UButton
                        label="Create Warehouse"
                        :loading="saving"
                        :disabled="!form.name.trim()"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </AppDetailLayout>
</template>
