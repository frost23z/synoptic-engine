<script setup lang="ts">
import type { PageResponse } from '~/types/api'
import type { OrganizationResponse } from '~/types/contacts'
import { email, required } from '~/utils/validators'

definePageMeta({ title: 'New Person' })
useHead({ title: 'New Person — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { submitting, errors, validate, run } = useFormSubmit({
    failureTitle: 'Failed to create person',
})

const { data: orgs } = await useAsyncData<OrganizationResponse[]>('orgs-for-person', () =>
    api<PageResponse<OrganizationResponse>>('/api/contacts/organizations').then((p) => p.content)
)
const orgOptions = computed(() => orgs.value?.map((o) => ({ label: o.name, value: o.id })) ?? [])

const form = reactive({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    jobTitle: '',
    organizationId: '',
})

function submit() {
    run({
        validate: () =>
            validate(form, {
                firstName: [required('First name is required')],
                lastName: [required('Last name is required')],
                email: [email()],
            }),
        call: () =>
            api<{ id: string }>('/api/contacts/persons', {
                method: 'POST',
                body: {
                    firstName: form.firstName,
                    lastName: form.lastName,
                    email: form.email || undefined,
                    phone: form.phone || undefined,
                    jobTitle: form.jobTitle || undefined,
                    organizationId: form.organizationId || undefined,
                },
            }),
        fieldHints: ['email'],
        onSuccess: (person) => {
            toast.add({ title: 'Person created', color: 'success' })
            router.push(`/contacts/persons/${person.id}`)
        },
    })
}
</script>

<template>
    <AppDetailLayout
        to="/contacts/persons"
        title="New Person"
        root-class="mx-auto max-w-xl space-y-6"
    >
        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="First name" required :error="errors.firstName">
                        <UInput v-model="form.firstName" placeholder="John" class="w-full" />
                    </UFormField>
                    <UFormField label="Last name" required :error="errors.lastName">
                        <UInput v-model="form.lastName" placeholder="Doe" class="w-full" />
                    </UFormField>
                </div>
                <UFormField label="Email" :error="errors.email">
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
                    <UButton label="Create Person" :loading="submitting" @click="submit" />
                </div>
            </form>
        </UCard>
    </AppDetailLayout>
</template>
