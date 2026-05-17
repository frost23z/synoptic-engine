<script setup lang="ts">
import type { OrganizationResponse } from '~/types/contacts'

definePageMeta({ title: 'New Person' })
useHead({ title: 'New Person — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()

const { data: orgs } = await useAsyncData<OrganizationResponse[]>('orgs-for-person', () =>
    api<OrganizationResponse[]>('/api/contacts/organizations')
)
const orgOptions = computed(() => orgs.value?.map((o) => ({ label: o.name, value: o.id })) ?? [])

const saving = ref(false)
const form = reactive({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    jobTitle: '',
    organizationId: '',
})

async function submit() {
    saving.value = true
    try {
        const person = await api<{ id: string }>('/api/contacts/persons', {
            method: 'POST',
            body: {
                firstName: form.firstName,
                lastName: form.lastName,
                email: form.email || undefined,
                phone: form.phone || undefined,
                jobTitle: form.jobTitle || undefined,
                organizationId: form.organizationId || undefined,
            },
        })
        toast.add({ title: 'Person created', color: 'success' })
        router.push(`/contacts/persons/${person.id}`)
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
                to="/contacts/persons"
            />
            <h2 class="text-highlighted text-xl font-semibold">New Person</h2>
        </div>

        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="First name" required>
                        <UInput v-model="form.firstName" placeholder="John" class="w-full" />
                    </UFormField>
                    <UFormField label="Last name" required>
                        <UInput v-model="form.lastName" placeholder="Doe" class="w-full" />
                    </UFormField>
                </div>
                <UFormField label="Email">
                    <UInput
                        v-model="form.email"
                        type="email"
                        placeholder="john@company.com"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Phone">
                    <UInput v-model="form.phone" placeholder="+1 555 000 0000" class="w-full" />
                </UFormField>
                <UFormField label="Job title">
                    <UInput
                        v-model="form.jobTitle"
                        placeholder="Account Executive"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Organization">
                    <USelect
                        v-model="form.organizationId"
                        :items="orgOptions"
                        placeholder="Select organization"
                        class="w-full"
                    />
                </UFormField>
                <div class="flex justify-end gap-2 pt-2">
                    <UButton
                        color="neutral"
                        variant="outline"
                        label="Cancel"
                        to="/contacts/persons"
                    />
                    <UButton
                        label="Create Person"
                        :loading="saving"
                        :disabled="!form.firstName || !form.lastName"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </div>
</template>
