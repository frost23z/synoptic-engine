<script setup lang="ts">
definePageMeta({ title: 'New Organization' })
useHead({ title: 'New Organization — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()

const saving = ref(false)
const form = reactive({ name: '', email: '', phone: '', website: '', address: '' })

async function submit() {
    saving.value = true
    try {
        const org = await api<{ id: string }>('/api/contacts/organizations', {
            method: 'POST',
            body: {
                name: form.name,
                email: form.email || undefined,
                phone: form.phone || undefined,
                website: form.website || undefined,
                address: form.address || undefined,
            },
        })
        toast.add({ title: 'Organization created', color: 'success' })
        router.push(`/contacts/organizations/${org.id}`)
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
            <UButton
                icon="i-tabler-arrow-left"
                color="neutral"
                variant="ghost"
                to="/contacts/organizations"
            />
            <h2 class="text-highlighted text-xl font-semibold">New Organization</h2>
        </div>

        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <UFormField label="Name" required>
                    <UInput v-model="form.name" placeholder="e.g. ACME Corp" class="w-full" />
                </UFormField>
                <UFormField label="Email">
                    <UInput
                        v-model="form.email"
                        type="email"
                        placeholder="contact@acme.com"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Phone">
                    <UInput v-model="form.phone" placeholder="+1 555 000 0000" class="w-full" />
                </UFormField>
                <UFormField label="Website">
                    <UInput v-model="form.website" placeholder="https://acme.com" class="w-full" />
                </UFormField>
                <UFormField label="Address">
                    <UInput v-model="form.address" class="w-full" />
                </UFormField>
                <div class="flex justify-end gap-2 pt-2">
                    <UButton
                        color="neutral"
                        variant="outline"
                        label="Cancel"
                        to="/contacts/organizations"
                    />
                    <UButton
                        label="Create Organization"
                        :loading="saving"
                        :disabled="!form.name"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </div>
</template>
