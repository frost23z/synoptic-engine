<script setup lang="ts">
import type { PersonResponse, OrganizationResponse } from '~/types/contacts'
import type { StageResponse } from '~/types/leads'

definePageMeta({ title: 'New Lead' })
useHead({ title: 'New Lead — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()

const { data: pipelines } = await useAsyncData('create-pipelines', () =>
    api<{ id: string; name: string }[]>('/api/pipelines')
)
const { data: persons } = await useAsyncData<PersonResponse[]>('create-persons', () =>
    api<PersonResponse[]>('/api/contacts/persons')
)
const { data: orgs } = await useAsyncData<OrganizationResponse[]>('create-orgs', () =>
    api<OrganizationResponse[]>('/api/contacts/organizations')
)
const { data: sources } = await useAsyncData<{ id: string; name: string }[]>('create-sources', () =>
    api<{ id: string; name: string }[]>('/api/lead-sources')
)
const { data: leadTypes } = await useAsyncData<{ id: string; name: string }[]>('create-types', () =>
    api<{ id: string; name: string }[]>('/api/lead-types')
)

const pipelineOptions = computed(
    () => pipelines.value?.map((p) => ({ label: p.name, value: p.id })) ?? []
)
const personOptions = computed(
    () => persons.value?.map((p) => ({ label: p.fullName, value: p.id })) ?? []
)
const orgOptions = computed(() => orgs.value?.map((o) => ({ label: o.name, value: o.id })) ?? [])
const sourceOptions = computed(
    () => sources.value?.map((s) => ({ label: s.name, value: s.id })) ?? []
)
const typeOptions = computed(
    () => leadTypes.value?.map((t) => ({ label: t.name, value: t.id })) ?? []
)

const stages = ref<StageResponse[]>([])
const stageOptions = computed(() => stages.value.map((s) => ({ label: s.name, value: s.id })))

const saving = ref(false)
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

watch(
    () => form.pipelineId,
    async (pid) => {
        if (!pid) {
            stages.value = []
            form.stageId = ''
            return
        }
        const s = await api<StageResponse[]>(`/api/pipelines/${pid}/stages`)
        stages.value = s
        form.stageId = s[0]?.id ?? ''
    }
)

// Auto-select default pipeline on load
watch(
    pipelines,
    (ps) => {
        if (ps?.length && !form.pipelineId) {
            const def =
                ps.find((p: { id: string; name: string; default?: boolean }) => p.default) ?? ps[0]
            if (def) form.pipelineId = def.id
        }
    },
    { immediate: true }
)

async function submit() {
    saving.value = true
    try {
        const lead = await api<{ id: string }>('/api/leads', {
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
        })
        toast.add({ title: 'Lead created', color: 'success' })
        router.push(`/leads/${lead.id}`)
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}
</script>

<template>
    <AppDetailLayout to="/leads" title="New Lead" root-class="mx-auto max-w-xl space-y-6">
        <UCard>
            <form class="space-y-4" @submit.prevent="submit">
                <UFormField label="Title" required>
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
                            :disabled="!stages.length"
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
                        :loading="saving"
                        :disabled="!form.title"
                        @click="submit"
                    />
                </div>
            </form>
        </UCard>
    </AppDetailLayout>
</template>
