<script setup lang="ts">
import type { PageResponse } from '~/types/api'
import type { PersonResponse, OrganizationResponse } from '~/types/contacts'
import { required } from '~/utils/validators'

definePageMeta({ title: 'New Lead' })
useHead({ title: 'New Lead — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { submitting, errors, validate, run } = useFormSubmit({
    failureTitle: 'Failed to create lead',
})

// Pipelines / sources / types come from the shared domain cache (fetched once).
const { pipelines, pipelineOptions, sourceOptions, typeOptions, defaultPipeline, loadLeadLookups } =
    useDomainLookups()
await loadLeadLookups()

// Contacts/orgs are paginated (PageResponse) — pull a generous page for the selects.
const { data: persons } = await useAsyncData<PersonResponse[]>('create-persons', () =>
    api<PageResponse<PersonResponse>>('/api/contacts/persons', { params: { size: 500 } }).then(
        (p) => p.content
    )
)
const { data: orgs } = await useAsyncData<OrganizationResponse[]>('create-orgs', () =>
    api<PageResponse<OrganizationResponse>>('/api/contacts/organizations', {
        params: { size: 500 },
    }).then((p) => p.content)
)

const personOptions = computed(
    () => persons.value?.map((p) => ({ label: p.fullName, value: p.id })) ?? []
)
const orgOptions = computed(() => orgs.value?.map((o) => ({ label: o.name, value: o.id })) ?? [])

const form = reactive({
    title: '',
    description: '',
    amount: '',
    expectedCloseDate: '',
    pipelineId: '',
    stageId: '',
    personId: '',
    organizationId: '',
    leadSourceId: '',
    leadTypeId: '',
})

// Stages are embedded in each pipeline from the list endpoint — no extra call.
const stageOptions = computed(() => {
    const pipeline = pipelines.value.find((p) => p.id === form.pipelineId)
    return (pipeline?.stages ?? []).map((s) => ({ label: s.name, value: s.id }))
})

watch(
    () => form.pipelineId,
    (pid) => {
        const pipeline = pipelines.value.find((p) => p.id === pid)
        form.stageId = pipeline?.stages?.[0]?.id ?? ''
    }
)

// Pre-select the tenant's default (or first) pipeline once lookups are loaded.
watch(
    defaultPipeline,
    (def) => {
        if (def && !form.pipelineId) form.pipelineId = def.id
    },
    { immediate: true }
)

function submit() {
    run({
        validate: () => validate(form, { title: [required('Title is required')] }),
        call: () =>
            api<{ id: string }>('/api/leads', {
                method: 'POST',
                body: {
                    title: form.title,
                    description: form.description || undefined,
                    amount: form.amount ? Number(form.amount) : undefined,
                    expectedCloseDate: form.expectedCloseDate || undefined,
                    pipelineId: form.pipelineId || undefined,
                    stageId: form.stageId || undefined,
                    personId: form.personId || undefined,
                    organizationId: form.organizationId || undefined,
                    leadSourceId: form.leadSourceId || undefined,
                    leadTypeId: form.leadTypeId || undefined,
                },
            }),
        onSuccess: (lead) => {
            toast.add({ title: 'Lead created', color: 'success' })
            router.push(`/leads/${lead.id}`)
        },
    })
}
</script>

<template>
    <AppDetailLayout to="/leads" title="New Lead" root-class="mx-auto max-w-xl space-y-6">
        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <UFormField label="Title" required :error="errors.title">
                    <UInput
                        v-model="form.title"
                        placeholder="e.g. ACME Corp Expansion"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UTextarea v-model="form.description" :rows="3" class="w-full" />
                </UFormField>
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Amount">
                        <UInput
                            v-model="form.amount"
                            type="number"
                            step="0.01"
                            placeholder="0.00"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Expected Close">
                        <UInput v-model="form.expectedCloseDate" type="date" class="w-full" />
                    </UFormField>
                </div>
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Pipeline">
                        <USelect
                            v-model="form.pipelineId"
                            :items="pipelineOptions"
                            placeholder="Select pipeline"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Stage">
                        <USelect
                            v-model="form.stageId"
                            :items="stageOptions"
                            placeholder="Select stage"
                            :disabled="!stageOptions.length"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <UFormField label="Contact">
                    <USelect
                        v-model="form.personId"
                        :items="personOptions"
                        placeholder="Select person"
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
                <div class="grid grid-cols-2 gap-4">
                    <UFormField label="Source">
                        <USelect
                            v-model="form.leadSourceId"
                            :items="sourceOptions"
                            placeholder="Select source"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Type">
                        <USelect
                            v-model="form.leadTypeId"
                            :items="typeOptions"
                            placeholder="Select type"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <div class="flex justify-end gap-2 pt-2">
                    <UButton color="neutral" variant="outline" label="Cancel" to="/leads" />
                    <UButton
                        label="Create Lead"
                        :loading="submitting"
                        :disabled="!form.title"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </AppDetailLayout>
</template>
