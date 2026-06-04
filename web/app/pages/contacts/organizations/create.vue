<script setup lang="ts">
import { email, required, url } from '~/utils/validators'

definePageMeta({ title: 'New Organization' })
useHead({ title: 'New Organization — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { submitting, errors, validate, run } = useFormSubmit({
    failureTitle: 'Failed to create organization',
})

const form = reactive({ name: '', email: '', phone: '', website: '', address: '' })

function submit() {
    run({
        validate: () =>
            validate(form, {
                name: [required('Name is required')],
                email: [email()],
                website: [url()],
            }),
        call: () =>
            api<{ id: string }>('/api/contacts/organizations', {
                method: 'POST',
                body: {
                    name: form.name,
                    email: form.email || undefined,
                    phone: form.phone || undefined,
                    website: form.website || undefined,
                    address: form.address || undefined,
                },
            }),
        fieldHints: ['email', 'name'],
        onSuccess: (org) => {
            toast.add({ title: 'Organization created', color: 'success' })
            router.push(`/contacts/organizations/${org.id}`)
        },
    })
}
</script>

<template>
    <AppDetailLayout
        to="/contacts/organizations"
        title="New Organization"
        root-class="mx-auto max-w-xl space-y-6"
    >
        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <UFormField label="Name" required :error="errors.name">
                    <UInput v-model="form.name" placeholder="e.g. ACME Corp" class="w-full" />
                </UFormField>
                <UFormField label="Email" :error="errors.email">
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
                <UFormField label="Website" :error="errors.website">
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
                    <UButton label="Create Organization" :loading="submitting" @click="submit" />
                </div>
            </form>
        </UCard>
    </AppDetailLayout>
</template>
