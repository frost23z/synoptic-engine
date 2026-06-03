<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { MarketingCampaignResponse, MarketingEventResponse } from '~/types/settings'

definePageMeta({ title: 'Marketing' })
useHead({ title: 'Marketing — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

// ── Events ────────────────────────────────────────────────────────────────
const {
    data: events,
    pending: eventsPending,
    refresh: refreshEvents,
} = await useAsyncData<MarketingEventResponse[]>('marketing-events', () =>
    api<MarketingEventResponse[]>('/api/settings/marketing/events')
)

const createEventOpen = ref(false)
const creatingEvent = ref(false)
const createEventForm = reactive({ name: '', description: '' })

function openCreateEvent() {
    Object.assign(createEventForm, { name: '', description: '' })
    createEventOpen.value = true
}

async function submitCreateEvent() {
    creatingEvent.value = true
    try {
        await api('/api/settings/marketing/events', {
            method: 'POST',
            body: {
                name: createEventForm.name,
                description: createEventForm.description || undefined,
            },
        })
        toast.add({ title: 'Event created', color: 'success' })
        createEventOpen.value = false
        refreshEvents()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to create event',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creatingEvent.value = false
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
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function eventRowActions(e: MarketingEventResponse): DropdownMenuItem[][] {
    return [
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

// ── Campaigns ─────────────────────────────────────────────────────────────
const {
    data: campaigns,
    pending: campaignsPending,
    refresh: refreshCampaigns,
} = await useAsyncData<MarketingCampaignResponse[]>('marketing-campaigns', () =>
    api<MarketingCampaignResponse[]>('/api/settings/marketing/campaigns')
)

const createCampaignOpen = ref(false)
const creatingCampaign = ref(false)
const createCampaignForm = reactive({
    name: '',
    subject: '',
    description: '',
    eventId: '',
})

function openCreateCampaign() {
    Object.assign(createCampaignForm, { name: '', subject: '', description: '', eventId: '' })
    createCampaignOpen.value = true
}

const eventOptions = computed(
    () => events.value?.map((e) => ({ label: e.name, value: e.id })) ?? []
)

async function submitCreateCampaign() {
    creatingCampaign.value = true
    try {
        await api('/api/settings/marketing/campaigns', {
            method: 'POST',
            body: {
                name: createCampaignForm.name,
                subject: createCampaignForm.subject,
                description: createCampaignForm.description || undefined,
                eventId: createCampaignForm.eventId || undefined,
            },
        })
        toast.add({ title: 'Campaign created', color: 'success' })
        createCampaignOpen.value = false
        refreshCampaigns()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to create campaign',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creatingCampaign.value = false
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
                <template #createdAt-cell="{ row }">
                    <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
                </template>
                <template #actions-cell="{ row }">
                    <AppRowActions
                        v-if="can('marketing.delete')"
                        :items="eventRowActions(row.original)"
                    />
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
                    <AppRowActions
                        v-if="can('marketing.delete')"
                        :items="campaignRowActions(row.original)"
                    />
                </template>
                <template #empty>
                    <AppEmptyState icon="i-tabler-mail-bolt" message="No campaigns yet" />
                </template>
            </AppListTable>
        </div>

        <!-- Create event modal -->
        <AppConfirmModal
            v-model:open="createEventOpen"
            title="Create Marketing Event"
            confirm-label="Create"
            :loading="creatingEvent"
            :confirm-disabled="!createEventForm.name"
            @confirm="submitCreateEvent"
        >
            <form class="space-y-3" @submit.prevent="submitCreateEvent">
                <UFormField label="Name" required>
                    <UInput
                        v-model="createEventForm.name"
                        placeholder="e.g. Trial Started"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="createEventForm.description"
                        placeholder="Optional"
                        class="w-full"
                    />
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

        <!-- Create campaign modal -->
        <AppConfirmModal
            v-model:open="createCampaignOpen"
            title="Create Campaign"
            confirm-label="Create"
            :loading="creatingCampaign"
            :confirm-disabled="!createCampaignForm.name || !createCampaignForm.subject"
            @confirm="submitCreateCampaign"
        >
            <form class="space-y-3" @submit.prevent="submitCreateCampaign">
                <UFormField label="Name" required>
                    <UInput
                        v-model="createCampaignForm.name"
                        placeholder="e.g. Welcome Email"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Subject" required>
                    <UInput
                        v-model="createCampaignForm.subject"
                        placeholder="e.g. Welcome to Synoptic!"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UInput
                        v-model="createCampaignForm.description"
                        placeholder="Optional"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Marketing Event">
                    <USelect
                        v-model="createCampaignForm.eventId"
                        :items="eventOptions"
                        placeholder="Link to event (optional)"
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
