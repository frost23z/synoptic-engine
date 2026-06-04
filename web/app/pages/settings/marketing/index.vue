<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { MarketingCampaignResponse, MarketingEventResponse } from '~/types/settings'

definePageMeta({ title: 'Marketing' })
useHead({ title: 'Marketing — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

// Backend gates marketing update + delete + execute on marketing.edit.
const canManage = computed(() => can('marketing.edit'))

// ── Events ────────────────────────────────────────────────────────────────
const {
    data: events,
    pending: eventsPending,
    refresh: refreshEvents,
} = await useAsyncData<MarketingEventResponse[]>('marketing-events', () =>
    api<MarketingEventResponse[]>('/api/settings/marketing/events')
)

const eventFormOpen = ref(false)
const savingEvent = ref(false)
const editingEventId = ref<string | null>(null)
const eventForm = reactive({ name: '', description: '', eventDate: '' })
const isEventEdit = computed(() => editingEventId.value !== null)

function openCreateEvent() {
    editingEventId.value = null
    Object.assign(eventForm, { name: '', description: '', eventDate: '' })
    eventFormOpen.value = true
}

function openEditEvent(e: MarketingEventResponse) {
    editingEventId.value = e.id
    Object.assign(eventForm, {
        name: e.name,
        description: e.description ?? '',
        eventDate: e.eventDate ?? '',
    })
    eventFormOpen.value = true
}

async function submitEvent() {
    savingEvent.value = true
    try {
        await api(
            isEventEdit.value
                ? `/api/settings/marketing/events/${editingEventId.value}`
                : '/api/settings/marketing/events',
            {
                method: isEventEdit.value ? 'PUT' : 'POST',
                body: {
                    name: eventForm.name,
                    description: eventForm.description || undefined,
                    eventDate: eventForm.eventDate || undefined,
                },
            }
        )
        toast.add({
            title: isEventEdit.value ? 'Event updated' : 'Event created',
            color: 'success',
        })
        eventFormOpen.value = false
        refreshEvents()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save event', description: e?.data?.message, color: 'error' })
    } finally {
        savingEvent.value = false
    }
}

const {
    open: deleteEventOpen,
    target: eventToDelete,
    deleting: deletingEvent,
    prompt: promptDeleteEvent,
    confirm: confirmDeleteEvent,
} = useDeleteResource<MarketingEventResponse>({
    endpoint: (e) => `/api/settings/marketing/events/${e.id}`,
    successMessage: 'Event deleted',
    onDeleted: refreshEvents,
})

const eventColumns: TableColumn<MarketingEventResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'description', header: 'Description' },
    { accessorKey: 'eventDate', header: 'Date' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function eventRowActions(e: MarketingEventResponse): DropdownMenuItem[][] {
    return [
        [{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEditEvent(e) }],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDeleteEvent(e),
            },
        ],
    ]
}

// ── Email templates (campaign lookup) ──────────────────────────────────────
const { data: templates } = await useAsyncData<{ id: string; name: string }[]>(
    'marketing-templates',
    () =>
        can('email-templates.view')
            ? api<{ id: string; name: string }[]>('/api/settings/email-templates')
            : Promise.resolve([])
)
const templateOptions = computed(() => [
    { label: 'None', value: '' },
    ...(templates.value?.map((t) => ({ label: t.name, value: t.id })) ?? []),
])

// ── Campaigns ─────────────────────────────────────────────────────────────
const {
    data: campaigns,
    pending: campaignsPending,
    refresh: refreshCampaigns,
} = await useAsyncData<MarketingCampaignResponse[]>('marketing-campaigns', () =>
    api<MarketingCampaignResponse[]>('/api/settings/marketing/campaigns')
)

const campaignFormOpen = ref(false)
const savingCampaign = ref(false)
const editingCampaignId = ref<string | null>(null)
const campaignForm = reactive({
    name: '',
    subject: '',
    description: '',
    eventId: '',
    emailTemplateId: '',
})
const isCampaignEdit = computed(() => editingCampaignId.value !== null)

const eventOptions = computed(() => [
    { label: 'None', value: '' },
    ...(events.value?.map((e) => ({ label: e.name, value: e.id })) ?? []),
])

function openCreateCampaign() {
    editingCampaignId.value = null
    Object.assign(campaignForm, {
        name: '',
        subject: '',
        description: '',
        eventId: '',
        emailTemplateId: '',
    })
    campaignFormOpen.value = true
}

function openEditCampaign(c: MarketingCampaignResponse) {
    editingCampaignId.value = c.id
    Object.assign(campaignForm, {
        name: c.name,
        subject: c.subject,
        description: c.description ?? '',
        eventId: c.eventId ?? '',
        emailTemplateId: c.emailTemplateId ?? '',
    })
    campaignFormOpen.value = true
}

async function submitCampaign() {
    savingCampaign.value = true
    try {
        await api(
            isCampaignEdit.value
                ? `/api/settings/marketing/campaigns/${editingCampaignId.value}`
                : '/api/settings/marketing/campaigns',
            {
                method: isCampaignEdit.value ? 'PUT' : 'POST',
                body: {
                    name: campaignForm.name,
                    subject: campaignForm.subject,
                    description: campaignForm.description || undefined,
                    eventId: campaignForm.eventId || undefined,
                    emailTemplateId: campaignForm.emailTemplateId || undefined,
                },
            }
        )
        toast.add({
            title: isCampaignEdit.value ? 'Campaign updated' : 'Campaign created',
            color: 'success',
        })
        campaignFormOpen.value = false
        refreshCampaigns()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to save campaign',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        savingCampaign.value = false
    }
}

const {
    open: deleteCampaignOpen,
    target: campaignToDelete,
    deleting: deletingCampaign,
    prompt: promptDeleteCampaign,
    confirm: confirmDeleteCampaign,
} = useDeleteResource<MarketingCampaignResponse>({
    endpoint: (c) => `/api/settings/marketing/campaigns/${c.id}`,
    successMessage: 'Campaign deleted',
    onDeleted: refreshCampaigns,
})

// ── Execute campaign ────────────────────────────────────────────────────────
const executeOpen = ref(false)
const executing = ref(false)
const executeTarget = shallowRef<MarketingCampaignResponse | null>(null)
const recipientsText = ref('')

const recipients = computed(() =>
    recipientsText.value
        .split(/[\s,;]+/)
        .map((r) => r.trim())
        .filter(Boolean)
)

function openExecute(c: MarketingCampaignResponse) {
    executeTarget.value = c
    recipientsText.value = ''
    executeOpen.value = true
}

async function submitExecute() {
    if (!executeTarget.value || !recipients.value.length) return
    executing.value = true
    try {
        const res = await api<{ requested: number; sent: number; queued: number }>(
            `/api/settings/marketing/campaigns/${executeTarget.value.id}/execute`,
            { method: 'POST', body: { recipients: recipients.value } }
        )
        toast.add({
            title: 'Campaign executed',
            description: `${res.sent} sent · ${res.queued} queued of ${res.requested} requested`,
            color: 'success',
        })
        executeOpen.value = false
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to execute', description: e?.data?.message, color: 'error' })
    } finally {
        executing.value = false
    }
}

const campaignColumns: TableColumn<MarketingCampaignResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'subject', header: 'Subject' },
    { id: 'event', header: 'Event' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function campaignRowActions(c: MarketingCampaignResponse): DropdownMenuItem[][] {
    return [
        [
            { label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEditCampaign(c) },
            { label: 'Execute', icon: 'i-tabler-send', onSelect: () => openExecute(c) },
        ],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDeleteCampaign(c),
            },
        ],
    ]
}

function eventNameById(id?: string) {
    if (!id) return null
    return events.value?.find((e) => e.id === id)?.name ?? null
}
</script>

<template>
    <div class="space-y-8">
        <!-- Events section -->
        <div class="space-y-4">
            <AppPageHeader
                title="Marketing Events"
                subtitle="Lifecycle hooks that trigger campaigns"
            >
                <template #actions>
                    <UButton
                        v-if="can('marketing.create')"
                        icon="i-tabler-plus"
                        label="New Event"
                        @click="openCreateEvent"
                    />
                </template>
            </AppPageHeader>

            <AppListTable :rows="events ?? []" :columns="eventColumns" :loading="eventsPending">
                <template #name-cell="{ row }">
                    <span class="font-medium">{{ row.original.name }}</span>
                </template>
                <template #description-cell="{ row }">
                    <span class="text-muted text-sm">{{ row.original.description ?? '—' }}</span>
                </template>
                <template #eventDate-cell="{ row }">
                    <span class="text-muted text-sm">{{
                        row.original.eventDate ? formatDate(row.original.eventDate) : '—'
                    }}</span>
                </template>
                <template #actions-cell="{ row }">
                    <AppRowActions v-if="canManage" :items="eventRowActions(row.original)" />
                </template>
                <template #empty>
                    <AppEmptyState
                        icon="i-tabler-calendar-event"
                        message="No marketing events yet"
                    />
                </template>
            </AppListTable>
        </div>

        <!-- Campaigns section -->
        <div class="space-y-4">
            <AppPageHeader title="Campaigns" subtitle="Email campaigns linked to marketing events">
                <template #actions>
                    <UButton
                        v-if="can('marketing.create')"
                        icon="i-tabler-plus"
                        label="New Campaign"
                        @click="openCreateCampaign"
                    />
                </template>
            </AppPageHeader>

            <AppListTable
                :rows="campaigns ?? []"
                :columns="campaignColumns"
                :loading="campaignsPending"
            >
                <template #name-cell="{ row }">
                    <div>
                        <p class="text-highlighted font-medium">{{ row.original.name }}</p>
                        <p v-if="row.original.description" class="text-muted text-xs">
                            {{ row.original.description }}
                        </p>
                    </div>
                </template>
                <template #subject-cell="{ row }">
                    <span class="text-muted text-sm">{{ row.original.subject }}</span>
                </template>
                <template #event-cell="{ row }">
                    <UBadge
                        v-if="eventNameById(row.original.eventId)"
                        :label="eventNameById(row.original.eventId)!"
                        color="neutral"
                        variant="soft"
                        size="sm"
                    />
                    <span v-else class="text-muted text-sm">—</span>
                </template>
                <template #createdAt-cell="{ row }">
                    <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
                </template>
                <template #actions-cell="{ row }">
                    <AppRowActions v-if="canManage" :items="campaignRowActions(row.original)" />
                </template>
                <template #empty>
                    <AppEmptyState icon="i-tabler-mail-bolt" message="No campaigns yet" />
                </template>
            </AppListTable>
        </div>

        <!-- Event create/edit modal -->
        <AppConfirmModal
            v-model:open="eventFormOpen"
            :title="isEventEdit ? 'Edit Event' : 'Create Marketing Event'"
            :confirm-label="isEventEdit ? 'Save' : 'Create'"
            :loading="savingEvent"
            :confirm-disabled="!eventForm.name"
            @confirm="submitEvent"
        >
            <form class="space-y-3" @submit.prevent="submitEvent">
                <UFormField label="Name" required>
                    <UInput
                        v-model="eventForm.name"
                        placeholder="e.g. Trial Started"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UInput v-model="eventForm.description" placeholder="Optional" class="w-full" />
                </UFormField>
                <UFormField label="Event date">
                    <UInput v-model="eventForm.eventDate" type="date" class="w-full" />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Delete event modal -->
        <AppConfirmModal
            v-model:open="deleteEventOpen"
            title="Delete Event"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deletingEvent"
            @confirm="confirmDeleteEvent"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ eventToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>

        <!-- Campaign create/edit modal -->
        <AppConfirmModal
            v-model:open="campaignFormOpen"
            :title="isCampaignEdit ? 'Edit Campaign' : 'Create Campaign'"
            :confirm-label="isCampaignEdit ? 'Save' : 'Create'"
            :loading="savingCampaign"
            :confirm-disabled="!campaignForm.name || !campaignForm.subject"
            width-class="sm:max-w-2xl"
            @confirm="submitCampaign"
        >
            <form class="space-y-3" @submit.prevent="submitCampaign">
                <UFormField label="Name" required>
                    <UInput
                        v-model="campaignForm.name"
                        placeholder="e.g. Welcome Email"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Subject" required>
                    <UInput
                        v-model="campaignForm.subject"
                        placeholder="e.g. Welcome to Synoptic!"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="campaignForm.description"
                        placeholder="Optional"
                        class="w-full"
                    />
                </UFormField>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Marketing event">
                        <USelect
                            v-model="campaignForm.eventId"
                            :items="eventOptions"
                            placeholder="Link to event"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Email template">
                        <USelect
                            v-model="campaignForm.emailTemplateId"
                            :items="templateOptions"
                            placeholder="Template"
                            class="w-full"
                        />
                    </UFormField>
                </div>
            </form>
        </AppConfirmModal>

        <!-- Execute campaign modal -->
        <AppConfirmModal
            v-model:open="executeOpen"
            title="Execute Campaign"
            confirm-label="Send"
            :loading="executing"
            :confirm-disabled="!recipients.length"
            @confirm="submitExecute"
        >
            <form class="space-y-3" @submit.prevent="submitExecute">
                <p class="text-muted text-sm">
                    Send
                    <strong class="text-highlighted">{{ executeTarget?.name }}</strong>
                    to the recipients below.
                </p>
                <UFormField
                    label="Recipients"
                    :help="`${recipients.length} address(es) — separate with commas, spaces or new lines.`"
                    required
                >
                    <UTextarea
                        v-model="recipientsText"
                        :rows="4"
                        placeholder="alice@example.com, bob@example.com"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Delete campaign modal -->
        <AppConfirmModal
            v-model:open="deleteCampaignOpen"
            title="Delete Campaign"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deletingCampaign"
            @confirm="confirmDeleteCampaign"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ campaignToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
