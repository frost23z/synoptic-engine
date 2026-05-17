<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { MarketingCampaignResponse, MarketingEventResponse } from '~/types/settings'

definePageMeta({ title: 'Marketing' })
useHead({ title: 'Marketing — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

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

const deleteEventOpen = ref(false)
const toDeleteEvent = ref<MarketingEventResponse | null>(null)
const deletingEvent = ref(false)

async function confirmDeleteEvent() {
    if (!toDeleteEvent.value) return
    deletingEvent.value = true
    try {
        await api(`/api/settings/marketing/events/${toDeleteEvent.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Event deleted', color: 'success' })
        deleteEventOpen.value = false
        refreshEvents()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deletingEvent.value = false
    }
}

const eventColumns: TableColumn<MarketingEventResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'description', header: 'Description' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function eventRowActions(e: MarketingEventResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDeleteEvent.value = e
                    deleteEventOpen.value = true
                },
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

const deleteCampaignOpen = ref(false)
const toDeleteCampaign = ref<MarketingCampaignResponse | null>(null)
const deletingCampaign = ref(false)

async function confirmDeleteCampaign() {
    if (!toDeleteCampaign.value) return
    deletingCampaign.value = true
    try {
        await api(`/api/settings/marketing/campaigns/${toDeleteCampaign.value.id}`, {
            method: 'DELETE',
        })
        toast.add({ title: 'Campaign deleted', color: 'success' })
        deleteCampaignOpen.value = false
        refreshCampaigns()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deletingCampaign.value = false
    }
}

const campaignColumns: TableColumn<MarketingCampaignResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'subject', header: 'Subject' },
    { id: 'event', header: 'Event' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function campaignRowActions(c: MarketingCampaignResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDeleteCampaign.value = c
                    deleteCampaignOpen.value = true
                },
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
            <div class="flex items-center justify-between">
                <div>
                    <h2 class="text-highlighted text-xl font-semibold">Marketing Events</h2>
                    <p class="text-muted text-sm">Lifecycle hooks that trigger campaigns</p>
                </div>
                <UButton icon="i-tabler-plus" label="New Event" @click="openCreateEvent" />
            </div>

            <UCard :ui="{ body: 'p-0' }">
                <UTable
                    :data="events ?? []"
                    :columns="eventColumns"
                    :loading="eventsPending"
                    sticky
                >
                    <template #name-cell="{ row }">
                        <span class="font-medium">{{ row.original.name }}</span>
                    </template>
                    <template #description-cell="{ row }">
                        <span class="text-muted text-sm">{{
                            row.original.description ?? '—'
                        }}</span>
                    </template>
                    <template #createdAt-cell="{ row }">
                        <span class="text-muted text-sm">{{
                            formatDate(row.original.createdAt)
                        }}</span>
                    </template>
                    <template #actions-cell="{ row }">
                        <UDropdownMenu :items="eventRowActions(row.original)">
                            <UButton
                                icon="i-tabler-dots-vertical"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                            />
                        </UDropdownMenu>
                    </template>
                    <template #empty>
                        <div class="space-y-2 py-10 text-center">
                            <UIcon
                                name="i-tabler-calendar-event"
                                class="text-muted mx-auto size-10"
                            />
                            <p class="text-muted text-sm">No marketing events yet</p>
                        </div>
                    </template>
                </UTable>
            </UCard>
        </div>

        <!-- Campaigns section -->
        <div class="space-y-4">
            <div class="flex items-center justify-between">
                <div>
                    <h2 class="text-highlighted text-xl font-semibold">Campaigns</h2>
                    <p class="text-muted text-sm">Email campaigns linked to marketing events</p>
                </div>
                <UButton icon="i-tabler-plus" label="New Campaign" @click="openCreateCampaign" />
            </div>

            <UCard :ui="{ body: 'p-0' }">
                <UTable
                    :data="campaigns ?? []"
                    :columns="campaignColumns"
                    :loading="campaignsPending"
                    sticky
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
                        <span class="text-muted text-sm">{{
                            formatDate(row.original.createdAt)
                        }}</span>
                    </template>
                    <template #actions-cell="{ row }">
                        <UDropdownMenu :items="campaignRowActions(row.original)">
                            <UButton
                                icon="i-tabler-dots-vertical"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                            />
                        </UDropdownMenu>
                    </template>
                    <template #empty>
                        <div class="space-y-2 py-10 text-center">
                            <UIcon name="i-tabler-mail-bolt" class="text-muted mx-auto size-10" />
                            <p class="text-muted text-sm">No campaigns yet</p>
                        </div>
                    </template>
                </UTable>
            </UCard>
        </div>

        <!-- Create event modal -->
        <UModal v-model:open="createEventOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create Marketing Event</p>
                    </template>
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
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="createEventOpen = false"
                            />
                            <UButton
                                label="Create"
                                :loading="creatingEvent"
                                :disabled="!createEventForm.name"
                                @click="submitCreateEvent"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete event modal -->
        <UModal v-model:open="deleteEventOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Event</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ toDeleteEvent?.name }}</strong
                        >? This cannot be undone.
                    </p>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="deleteEventOpen = false"
                            />
                            <UButton
                                color="error"
                                label="Delete"
                                :loading="deletingEvent"
                                @click="confirmDeleteEvent"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Create campaign modal -->
        <UModal v-model:open="createCampaignOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create Campaign</p>
                    </template>
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
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="createCampaignOpen = false"
                            />
                            <UButton
                                label="Create"
                                :loading="creatingCampaign"
                                :disabled="!createCampaignForm.name || !createCampaignForm.subject"
                                @click="submitCreateCampaign"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete campaign modal -->
        <UModal v-model:open="deleteCampaignOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Campaign</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ toDeleteCampaign?.name }}</strong
                        >? This cannot be undone.
                    </p>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="deleteCampaignOpen = false"
                            />
                            <UButton
                                color="error"
                                label="Delete"
                                :loading="deletingCampaign"
                                @click="confirmDeleteCampaign"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>
    </div>
</template>
