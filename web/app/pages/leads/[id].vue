<script setup lang="ts">
import type { LeadResponse, TagResponse, StageResponse } from '~/types/leads'
import type { PersonResponse, OrganizationResponse } from '~/types/contacts'
import type { ActivityResponse } from '~/types/activities'
import { ACTIVITY_TYPE_ICON } from '~/types/activities'

definePageMeta({ title: 'Lead' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { formatCurrency, formatDate, formatRelativeDate } = useFormatters()
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
const deleteOpen = ref(false)
const deleting = ref(false)

async function confirmDelete() {
    deleting.value = true
    try {
        await api(`/api/leads/${id}`, { method: 'DELETE' })
        toast.add({ title: 'Lead deleted', color: 'success' })
        router.push('/leads')
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Tags ──────────────────────────────────────────────────────────────────
const tagSearch = ref('')
const addingTag = ref(false)

const filteredTags = computed(() => {
    const existing = new Set(lead.value?.tags.map((t) => t.id) ?? [])
    return (allTags.value ?? []).filter(
        (t) => !existing.has(t.id) && t.name.toLowerCase().includes(tagSearch.value.toLowerCase())
    )
})

async function addTag(tag: TagResponse) {
    addingTag.value = true
    try {
        await api(`/api/leads/${id}/tags`, { method: 'POST', body: { tagId: tag.id } })
        refresh()
    } catch {
        toast.add({ title: 'Failed to add tag', color: 'error' })
    } finally {
        addingTag.value = false
    }
}

async function removeTag(tagId: string) {
    try {
        await api(`/api/leads/${id}/tags/${tagId}`, { method: 'DELETE' })
        refresh()
    } catch {
        toast.add({ title: 'Failed to remove tag', color: 'error' })
    }
}

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
    <div v-if="lead" class="space-y-6">
        <!-- Header -->
        <div class="flex items-start justify-between">
            <div class="flex items-center gap-3">
                <UButton icon="i-tabler-arrow-left" color="neutral" variant="ghost" to="/leads" />
                <div>
                    <h2 class="text-highlighted text-xl font-semibold">{{ lead.title }}</h2>
                    <div class="text-muted mt-0.5 flex items-center gap-2 text-sm">
                        <UBadge
                            :label="lead.status.charAt(0) + lead.status.slice(1).toLowerCase()"
                            :color="
                                lead.status === 'open'
                                    ? 'info'
                                    : lead.status === 'won'
                                      ? 'success'
                                      : 'error'
                            "
                            variant="soft"
                            size="xs"
                        />
                        <span v-if="pipelineName">{{ pipelineName }}</span>
                        <span v-if="stageName">· {{ stageName }}</span>
                    </div>
                </div>
            </div>
            <div class="flex gap-2">
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
                    @click="deleteOpen = true"
                />
            </div>
        </div>

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
                                icon="i-tabler-plus"
                                size="xs"
                                color="neutral"
                                variant="outline"
                                label="Add"
                                @click="openAddActivity"
                            />
                        </div>
                    </template>
                    <div v-if="!activities?.length" class="text-muted py-6 text-center text-sm">
                        No activities yet
                    </div>
                    <ul v-else class="divide-default divide-y">
                        <li
                            v-for="act in activities"
                            :key="act.id"
                            class="flex items-start gap-3 py-3"
                            :class="act.done ? 'opacity-60' : ''"
                        >
                            <div class="bg-muted mt-0.5 shrink-0 rounded-full p-1.5">
                                <UIcon
                                    :name="
                                        ACTIVITY_TYPE_ICON[
                                            act.type as keyof typeof ACTIVITY_TYPE_ICON
                                        ] ?? 'i-tabler-activity'
                                    "
                                    class="text-muted size-3.5"
                                />
                            </div>
                            <div class="min-w-0 flex-1">
                                <p
                                    class="text-default text-sm font-medium"
                                    :class="act.done ? 'line-through' : ''"
                                >
                                    {{ act.title }}
                                </p>
                                <p v-if="act.comment" class="text-muted mt-0.5 text-xs">
                                    {{ act.comment }}
                                </p>
                                <p class="text-muted mt-0.5 text-xs">
                                    {{ formatRelativeDate(act.scheduleFrom) }}
                                </p>
                            </div>
                            <UButton
                                :icon="
                                    act.done ? 'i-tabler-circle-check-filled' : 'i-tabler-circle'
                                "
                                :color="act.done ? 'success' : 'neutral'"
                                variant="ghost"
                                size="xs"
                                @click="toggleActivityDone(act)"
                            />
                        </li>
                    </ul>
                </UCard>
            </div>

            <!-- Right sidebar -->
            <div class="space-y-4">
                <!-- Tags -->
                <UCard>
                    <template #header><p class="text-highlighted font-semibold">Tags</p></template>
                    <div class="space-y-3">
                        <div class="flex flex-wrap gap-1.5">
                            <span
                                v-for="tag in lead.tags"
                                :key="tag.id"
                                class="border-default flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium"
                                :style="{ borderColor: tag.color, color: tag.color }"
                            >
                                {{ tag.name }}
                                <button class="hover:opacity-70" @click="removeTag(tag.id)">
                                    <UIcon name="i-tabler-x" class="size-3" />
                                </button>
                            </span>
                            <span v-if="!lead.tags.length" class="text-muted text-xs">No tags</span>
                        </div>
                        <UInput
                            v-model="tagSearch"
                            placeholder="Add tag…"
                            size="sm"
                            icon="i-tabler-search"
                        />
                        <div class="max-h-36 space-y-1 overflow-y-auto">
                            <button
                                v-for="tag in filteredTags"
                                :key="tag.id"
                                class="hover:bg-muted flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs"
                                :disabled="addingTag"
                                @click="addTag(tag)"
                            >
                                <span
                                    class="size-2 rounded-full"
                                    :style="{ backgroundColor: tag.color ?? '#888' }"
                                />
                                {{ tag.name }}
                            </button>
                        </div>
                    </div>
                </UCard>

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
        <UModal v-model:open="editing" :ui="{ content: 'sm:max-w-2xl' }">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Edit Lead</p></template
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
                                <UInput
                                    v-model="editForm.expectedCloseDate"
                                    type="date"
                                    class="w-full"
                                />
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
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="editing = false"
                            />
                            <UButton
                                label="Save"
                                :loading="saving"
                                :disabled="!editForm.title"
                                @click="submitEdit"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Add activity modal -->
        <UModal v-model:open="addActivityOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Add Activity</p></template
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
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="addActivityOpen = false"
                            />
                            <UButton
                                label="Add"
                                :loading="addingActivity"
                                :disabled="!activityForm.title"
                                @click="submitActivity"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete modal -->
        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Lead</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ lead.title }}</strong
                        >? This cannot be undone.
                    </p>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="deleteOpen = false"
                            />
                            <UButton
                                color="error"
                                label="Delete"
                                :loading="deleting"
                                @click="confirmDelete"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>
    </div>

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
