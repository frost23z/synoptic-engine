<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { WebhookResponse } from '~/types/settings'

definePageMeta({ title: 'Webhooks' })
useHead({ title: 'Webhooks — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

const KNOWN_EVENTS = [
    'lead.created',
    'lead.updated',
    'lead.stage.changed',
    'person.created',
    'quote.created',
]

const {
    data: webhooks,
    pending,
    refresh,
} = await useAsyncData<WebhookResponse[]>('webhooks', () =>
    api<WebhookResponse[]>('/api/settings/webhooks')
)

// ── Create modal ──────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({
    name: '',
    payloadUrl: '',
    events: [] as string[],
    active: true,
})

function openCreate() {
    Object.assign(createForm, { name: '', payloadUrl: '', events: [], active: true })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/settings/webhooks', {
            method: 'POST',
            body: {
                name: createForm.name,
                payloadUrl: createForm.payloadUrl,
                events: createForm.events,
                active: createForm.active,
            },
        })
        toast.add({ title: 'Webhook created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to create webhook',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creating.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<WebhookResponse | null>(null)
const deleting = ref(false)

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/settings/webhooks/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Webhook deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Table columns ─────────────────────────────────────────────────────────
const columns: TableColumn<WebhookResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'payloadUrl', header: 'URL' },
    { id: 'events', header: 'Events' },
    { id: 'status', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(w: WebhookResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDelete.value = w
                    deleteOpen.value = true
                },
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Webhooks</h2>
                <p class="text-muted text-sm">Send HTTP notifications on CRM events</p>
            </div>
            <UButton icon="i-tabler-plus" label="New Webhook" @click="openCreate" />
        </div>

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="webhooks ?? []" :columns="columns" :loading="pending" sticky>
                <template #name-cell="{ row }">
                    <span class="font-medium">{{ row.original.name }}</span>
                </template>
                <template #payloadUrl-cell="{ row }">
                    <span class="text-muted max-w-xs truncate text-sm">{{
                        row.original.payloadUrl
                    }}</span>
                </template>
                <template #events-cell="{ row }">
                    <UBadge
                        :label="`${row.original.events.length} event${row.original.events.length === 1 ? '' : 's'}`"
                        color="neutral"
                        variant="soft"
                        size="sm"
                    />
                </template>
                <template #status-cell="{ row }">
                    <UBadge
                        :label="row.original.active ? 'Active' : 'Inactive'"
                        :color="row.original.active ? 'success' : 'neutral'"
                        variant="soft"
                        size="sm"
                    />
                </template>
                <template #createdAt-cell="{ row }">
                    <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
                </template>
                <template #actions-cell="{ row }">
                    <UDropdownMenu :items="rowActions(row.original)">
                        <UButton
                            icon="i-tabler-dots-vertical"
                            color="neutral"
                            variant="ghost"
                            size="xs"
                        />
                    </UDropdownMenu>
                </template>
                <template #empty>
                    <div class="space-y-2 py-12 text-center">
                        <UIcon name="i-tabler-webhook" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No webhooks yet</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <!-- Create modal -->
        <UModal v-model:open="createOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create Webhook</p>
                    </template>
                    <form class="space-y-3" @submit.prevent="submitCreate">
                        <UFormField label="Name" required>
                            <UInput
                                v-model="createForm.name"
                                placeholder="e.g. Notify Slack on new lead"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Payload URL" required>
                            <UInput
                                v-model="createForm.payloadUrl"
                                placeholder="https://example.com/hook"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Events">
                            <div class="border-default space-y-1.5 rounded-md border p-3">
                                <label
                                    v-for="event in KNOWN_EVENTS"
                                    :key="event"
                                    class="border-default hover:bg-muted flex cursor-pointer items-center gap-2 rounded-md border px-2 py-1.5 text-sm transition-colors"
                                    :class="
                                        createForm.events.includes(event)
                                            ? 'bg-primary/10 border-primary text-primary'
                                            : ''
                                    "
                                >
                                    <input
                                        type="checkbox"
                                        class="hidden"
                                        :checked="createForm.events.includes(event)"
                                        @change="
                                            createForm.events.includes(event)
                                                ? createForm.events.splice(
                                                      createForm.events.indexOf(event),
                                                      1
                                                  )
                                                : createForm.events.push(event)
                                        "
                                    />
                                    {{ event }}
                                </label>
                            </div>
                            <p class="text-muted mt-1 text-xs">
                                {{ createForm.events.length }} selected
                            </p>
                        </UFormField>
                        <USwitch v-model="createForm.active" label="Active" />
                    </form>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="createOpen = false"
                            />
                            <UButton
                                label="Create"
                                :loading="creating"
                                :disabled="!createForm.name || !createForm.payloadUrl"
                                @click="submitCreate"
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
                        ><p class="text-highlighted font-semibold">Delete Webhook</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ toDelete?.name }}</strong
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
</template>
