<script setup lang="ts">
import type { LeadResponse, TagResponse, StageResponse } from '~/types/leads'
import type { PersonResponse, OrganizationResponse } from '~/types/contacts'
import type { ActivityResponse } from '~/types/activities'
import { LEAD_STATUS_COLOR, LEAD_STATUS_LABEL } from '~/types/leads'

definePageMeta({ title: 'Lead' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { formatCurrency, formatDate } = useFormatters()
const { can } = usePermissions()
const id = route.params.id as string

const {
    data: lead,
    pending: leadPending,
    refresh,
} = await useAsyncData<LeadResponse>(`lead-${id}`, () => api<LeadResponse>(`/api/leads/${id}`))
const pageTitle = computed(() =>
    lead.value?.title ? `${lead.value.title} — Synoptic` : 'Lead — Synoptic'
)
useHead({ title: pageTitle })

// ── Lookup data ───────────────────────────────────────────────────────────
const { data: pipelines } = await useAsyncData('pipelines-lookup', () =>
    api<{ id: string; name: string; stages?: StageResponse[] }[]>('/api/pipelines')
)
const { data: persons } = await useAsyncData<PersonResponse[]>('persons-lookup', () =>
    api<PersonResponse[]>('/api/contacts/persons')
)
const { data: orgs } = await useAsyncData<OrganizationResponse[]>('orgs-lookup2', () =>
    api<OrganizationResponse[]>('/api/contacts/organizations')
)
const { data: allTags } = await useAsyncData<TagResponse[]>('tags-lookup2', () =>
    api<TagResponse[]>('/api/tags')
)
const { data: leadSources } = await useAsyncData<{ id: string; name: string }[]>(
    'sources-lookup',
    () => api<{ id: string; name: string }[]>('/api/lead-sources')
)
const { data: leadTypesList } = await useAsyncData<{ id: string; name: string }[]>(
    'types-lookup',
    () => api<{ id: string; name: string }[]>('/api/lead-types')
)

const pipelineOptions = computed(
    () => pipelines.value?.map((p) => ({ label: p.name, value: p.id })) ?? []
)
const personOptions = computed(
    () => persons.value?.map((p) => ({ label: p.fullName, value: p.id })) ?? []
)
const orgOptions = computed(() => orgs.value?.map((o) => ({ label: o.name, value: o.id })) ?? [])
const sourceOptions = computed(
    () => leadSources.value?.map((s) => ({ label: s.name, value: s.id })) ?? []
)
const typeOptions = computed(
    () => leadTypesList.value?.map((t) => ({ label: t.name, value: t.id })) ?? []
)

const pipelineName = computed(
    () => pipelines.value?.find((p) => p.id === lead.value?.pipelineId)?.name
)
const personName = computed(
    () => persons.value?.find((p) => p.id === lead.value?.personId)?.fullName
)
const orgName = computed(() => orgs.value?.find((o) => o.id === lead.value?.organizationId)?.name)

// Load stages when pipeline is known
const { data: stages, refresh: refreshStages } = await useAsyncData<StageResponse[]>(
    `lead-${id}-stages`,
    () =>
        lead.value?.pipelineId
            ? api<StageResponse[]>(`/api/pipelines/${lead.value.pipelineId}/stages`)
            : Promise.resolve([])
)
const stageName = computed(() => stages.value?.find((s) => s.id === lead.value?.stageId)?.name)
const stageOptions = computed(
    () => stages.value?.map((s) => ({ label: s.name, value: s.id })) ?? []
)

// ── Edit ──────────────────────────────────────────────────────────────────
const editing = ref(false)
const saving = ref(false)
const editForm = reactive({
    title: '',
    description: '',
    amount: 0,
    expectedCloseDate: '',
    pipelineId: '',
    stageId: '',
    personId: '',
    organizationId: '',
    status: 'OPEN',
    lostReason: '',
    leadSourceId: '',
    leadTypeId: '',
})

function openEdit() {
    if (!lead.value) return
    Object.assign(editForm, {
        title: lead.value.title,
        description: lead.value.description ?? '',
        amount: lead.value.amount ?? 0,
        expectedCloseDate: lead.value.expectedCloseDate
            ? lead.value.expectedCloseDate.slice(0, 10)
            : '',
        pipelineId: lead.value.pipelineId,
        stageId: lead.value.stageId,
        personId: lead.value.personId ?? '',
        organizationId: lead.value.organizationId ?? '',
        status: lead.value.status.toUpperCase(),
        lostReason: lead.value.lostReason ?? '',
        leadSourceId: lead.value.leadSourceId ?? '',
        leadTypeId: lead.value.leadTypeId ?? '',
    })
    editing.value = true
}

watch(
    () => editForm.pipelineId,
    (pid) => {
        if (pid && pid !== lead.value?.pipelineId) {
            api<StageResponse[]>(`/api/pipelines/${pid}/stages`).then((s) => {
                stages.value = s
                editForm.stageId = s[0]?.id ?? ''
            })
        }
    }
)

async function submitEdit() {
    saving.value = true
    try {
        await api(`/api/leads/${id}`, {
            method: 'PUT',
            body: {
                title: editForm.title,
                description: editForm.description || undefined,
                amount: editForm.amount || undefined,
                expectedCloseDate: editForm.expectedCloseDate || undefined,
                pipelineId: editForm.pipelineId || undefined,
                stageId: editForm.stageId || undefined,
                personId: editForm.personId || undefined,
                organizationId: editForm.organizationId || undefined,
                status: editForm.status,
                lostReason: editForm.lostReason || undefined,
                leadSourceId: editForm.leadSourceId || undefined,
                leadTypeId: editForm.leadTypeId || undefined,
            },
        })
        toast.add({ title: 'Saved', color: 'success' })
        editing.value = false
        refresh()
        refreshStages()
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
} = useDeleteResource<LeadResponse>({
    endpoint: (l) => `/api/leads/${l.id}`,
    successMessage: 'Lead deleted',
    onDeleted: () => {
        router.push('/leads')
    },
})

// ── Activities ────────────────────────────────────────────────────────────
const { data: activities, refresh: refreshActivities } = await useAsyncData<ActivityResponse[]>(
    `lead-${id}-activities`,
    () => api<ActivityResponse[]>('/api/activities', { params: { leadId: id, size: 50 } })
)

const addActivityOpen = ref(false)
const addingActivity = ref(false)
const activityForm = reactive({
    title: '',
    type: 'CALL',
    scheduleFrom: '',
    scheduleTo: '',
    comment: '',
})

const ACTIVITY_TYPES = ['CALL', 'EMAIL', 'MEETING', 'TASK', 'NOTE', 'MESSAGE']

function openAddActivity() {
    const now = new Date().toISOString().slice(0, 16)
    Object.assign(activityForm, {
        title: '',
        type: 'CALL',
        scheduleFrom: now,
        scheduleTo: now,
        comment: '',
    })
    addActivityOpen.value = true
}

async function submitActivity() {
    addingActivity.value = true
    try {
        await api('/api/activities', {
            method: 'POST',
            body: {
                title: activityForm.title,
                type: activityForm.type,
                scheduleFrom: activityForm.scheduleFrom,
                scheduleTo: activityForm.scheduleTo,
                comment: activityForm.comment || undefined,
                leadId: id,
            },
        })
        toast.add({ title: 'Activity added', color: 'success' })
        addActivityOpen.value = false
        refreshActivities()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to add activity',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        addingActivity.value = false
    }
}

async function toggleActivityDone(act: ActivityResponse) {
    try {
        await api(`/api/activities/${act.id}/done`, { method: 'PATCH', body: { done: !act.done } })
        refreshActivities()
    } catch {
        toast.add({ title: 'Failed to update', color: 'error' })
    }
}

const LEAD_STATUS_OPTIONS = [
    { label: 'Open', value: 'OPEN' },
    { label: 'Won', value: 'WON' },
    { label: 'Lost', value: 'LOST' },
    { label: 'Abandoned', value: 'ABANDONED' },
]
</script>

<template>
    <AppDetailLayout v-if="lead" to="/leads" :title="lead.title">
        <template #subtitle>
            <div class="text-muted mt-0.5 flex items-center gap-2 text-sm">
                <UBadge
                    :label="LEAD_STATUS_LABEL[lead.status]"
                    :color="LEAD_STATUS_COLOR[lead.status]"
                    variant="soft"
                    size="xs"
                />
                <span v-if="pipelineName">{{ pipelineName }}</span>
                <span v-if="stageName">· {{ stageName }}</span>
            </div>
        </template>
        <template #actions>
            <UButton
                v-if="can('leads.edit')"
                icon="i-tabler-pencil"
                label="Edit"
                color="neutral"
                variant="outline"
                @click="openEdit"
            />
            <UButton
                v-if="can('leads.delete')"
                icon="i-tabler-trash"
                color="error"
                variant="outline"
                @click="promptDelete(lead)"
            />
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <!-- Left: details + activities -->
            <div class="space-y-6 lg:col-span-2">
                <!-- Details card -->
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Details</p></template
                    >
                    <dl class="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                        <div>
                            <dt class="text-muted">Amount</dt>
                            <dd class="text-highlighted mt-0.5 text-lg font-bold">
                                {{ lead.amount ? formatCurrency(lead.amount) : '—' }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Expected Close</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{
                                    lead.expectedCloseDate
                                        ? formatDate(lead.expectedCloseDate)
                                        : '—'
                                }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Contact</dt>
                            <dd class="mt-0.5">
                                <NuxtLink
                                    v-if="personName && lead.personId"
                                    :to="`/contacts/persons/${lead.personId}`"
                                    class="text-primary hover:underline"
                                >
                                    {{ personName }}
                                </NuxtLink>
                                <span v-else class="text-highlighted">—</span>
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Organization</dt>
                            <dd class="mt-0.5">
                                <NuxtLink
                                    v-if="orgName && lead.organizationId"
                                    :to="`/contacts/organizations/${lead.organizationId}`"
                                    class="text-primary hover:underline"
                                >
                                    {{ orgName }}
                                </NuxtLink>
                                <span v-else class="text-highlighted">—</span>
                            </dd>
                        </div>
                        <div v-if="lead.description" class="col-span-2">
                            <dt class="text-muted">Description</dt>
                            <dd class="text-highlighted mt-0.5">{{ lead.description }}</dd>
                        </div>
                        <div v-if="lead.lostReason" class="col-span-2">
                            <dt class="text-muted">Lost Reason</dt>
                            <dd class="text-highlighted mt-0.5">{{ lead.lostReason }}</dd>
                        </div>
                    </dl>
                </UCard>

                <!-- Activities -->
                <UCard>
                    <template #header>
                        <div class="flex items-center justify-between">
                            <p class="text-highlighted font-semibold">Activities</p>
                            <UButton
                                v-if="can('activities.create')"
                                icon="i-tabler-plus"
                                size="xs"
                                color="neutral"
                                variant="outline"
                                label="Add"
                                @click="openAddActivity"
                            />
                        </div>
                    </template>
                    <EntityTimeline
                        :activities="activities ?? []"
                        :can-toggle="can('activities.edit')"
                        @toggle-done="toggleActivityDone"
                    />
                </UCard>
            </div>

            <!-- Right sidebar -->
            <div class="space-y-4">
                <AppTagManager
                    :tags="lead.tags"
                    :all-tags="allTags ?? []"
                    :endpoint="`/api/leads/${id}/tags`"
                    :can-edit="can('leads.edit')"
                    @changed="refresh"
                />

                <!-- Meta -->
                <UCard>
                    <template #header><p class="text-highlighted font-semibold">Info</p></template>
                    <dl class="space-y-2 text-sm">
                        <div class="flex justify-between">
                            <dt class="text-muted">Created</dt>
                            <dd class="text-highlighted">{{ formatDate(lead.createdAt) }}</dd>
                        </div>
                        <div class="flex justify-between">
                            <dt class="text-muted">Updated</dt>
                            <dd class="text-highlighted">{{ formatDate(lead.updatedAt) }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editing"
            title="Edit Lead"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.title"
            width-class="sm:max-w-2xl"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <UFormField label="Title" required>
                    <UInput v-model="editForm.title" class="w-full" />
                </UFormField>
                <UFormField label="Description">
                    <UTextarea v-model="editForm.description" :rows="3" class="w-full" />
                </UFormField>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Amount">
                        <UInput
                            v-model.number="editForm.amount"
                            type="number"
                            step="0.01"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Expected Close">
                        <UInput v-model="editForm.expectedCloseDate" type="date" class="w-full" />
                    </UFormField>
                </div>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Pipeline">
                        <USelect
                            v-model="editForm.pipelineId"
                            :items="pipelineOptions"
                            placeholder="Select pipeline"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Stage">
                        <USelect
                            v-model="editForm.stageId"
                            :items="stageOptions"
                            placeholder="Select stage"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Contact">
                        <USelect
                            v-model="editForm.personId"
                            :items="personOptions"
                            placeholder="Select person"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Organization">
                        <USelect
                            v-model="editForm.organizationId"
                            :items="orgOptions"
                            placeholder="Select org"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Source">
                        <USelect
                            v-model="editForm.leadSourceId"
                            :items="sourceOptions"
                            placeholder="Select source"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Type">
                        <USelect
                            v-model="editForm.leadTypeId"
                            :items="typeOptions"
                            placeholder="Select type"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <UFormField label="Status">
                    <USelect
                        v-model="editForm.status"
                        :items="LEAD_STATUS_OPTIONS"
                        class="w-full"
                    />
                </UFormField>
                <UFormField v-if="editForm.status === 'LOST'" label="Lost Reason">
                    <UInput v-model="editForm.lostReason" class="w-full" />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Add activity modal -->
        <AppConfirmModal
            v-model:open="addActivityOpen"
            title="Add Activity"
            confirm-label="Add"
            :loading="addingActivity"
            :confirm-disabled="!activityForm.title"
            @confirm="submitActivity"
        >
            <form class="space-y-3" @submit.prevent="submitActivity">
                <UFormField label="Title" required>
                    <UInput
                        v-model="activityForm.title"
                        placeholder="e.g. Follow-up call"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Type" required>
                    <USelect
                        v-model="activityForm.type"
                        :items="ACTIVITY_TYPES.map((t) => ({ label: t, value: t }))"
                        class="w-full"
                    />
                </UFormField>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="From" required>
                        <UInput
                            v-model="activityForm.scheduleFrom"
                            type="datetime-local"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="To" required>
                        <UInput
                            v-model="activityForm.scheduleTo"
                            type="datetime-local"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <UFormField label="Comment">
                    <UTextarea v-model="activityForm.comment" :rows="3" class="w-full" />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Lead"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ lead.title }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </AppDetailLayout>

    <div v-else-if="leadPending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <div class="grid grid-cols-3 gap-6">
            <div class="col-span-2 space-y-3">
                <USkeleton class="h-40 w-full" />
                <USkeleton class="h-48 w-full" />
            </div>
            <USkeleton class="h-48 w-full" />
        </div>
    </div>
</template>
