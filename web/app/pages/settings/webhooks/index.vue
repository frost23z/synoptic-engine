<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { WebhookResponse } from '~/types/settings'
import { required, url } from '~/utils/validators'

definePageMeta({ title: 'Webhooks' })
useHead({ title: 'Webhooks — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()
const {
    submitting: creating,
    errors,
    run,
    validate,
    clearErrors,
} = useFormSubmit({ failureTitle: 'Failed to create webhook' })

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

// ── Create ──────────────────────────────────────────────────────────────
const createOpen = ref(false)
const createForm = reactive({
    name: '',
    payloadUrl: '',
    events: [] as string[],
    active: true,
})

function openCreate() {
    clearErrors()
    Object.assign(createForm, { name: '', payloadUrl: '', events: [], active: true })
    createOpen.value = true
}

function submitCreate() {
    run({
        validate: () =>
            validate(createForm, {
                name: [required('Name is required')],
                payloadUrl: [required('Payload URL is required'), url('Enter a valid URL')],
            }),
        call: () =>
            api('/api/settings/webhooks', {
                method: 'POST',
                body: {
                    name: createForm.name,
                    payloadUrl: createForm.payloadUrl,
                    events: createForm.events,
                    isActive: createForm.active,
                },
            }),
        fieldHints: ['name', 'payloadUrl'],
        onSuccess: () => {
            toast.add({ title: 'Webhook created', color: 'success' })
            createOpen.value = false
            refresh()
        },
    })
}

// ── Delete ──────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: webhookToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<WebhookResponse>({
    endpoint: (w) => `/api/settings/webhooks/${w.id}`,
    successMessage: 'Webhook deleted',
    onDeleted: refresh,
})

const columns: TableColumn<WebhookResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'payloadUrl', header: 'URL' },
    { id: 'events', header: 'Events' },
    { id: 'status', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

// Backend gates webhook update + delete + test on automations.edit.
const canManage = computed(() => can('automations.edit'))

function rowActions(w: WebhookResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [{ label: 'View', icon: 'i-tabler-eye', to: `/settings/webhooks/${w.id}` }],
    ]
    if (canManage.value) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(w),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Webhooks" subtitle="Send HTTP notifications on CRM events">
            <template #actions>
                <UButton
                    v-if="can('automations.create')"
                    icon="i-tabler-plus"
                    label="New Webhook"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="webhooks ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <NuxtLink
                    :to="`/settings/webhooks/${row.original.id}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.name }}
                </NuxtLink>
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
                    :label="row.original.isActive ? 'Active' : 'Inactive'"
                    :color="row.original.isActive ? 'success' : 'neutral'"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-webhook" message="No webhooks yet" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Webhook"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.name || !createForm.payloadUrl"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Name" required :error="errors.name">
                    <UInput
                        v-model="createForm.name"
                        placeholder="e.g. Notify Slack on new lead"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Payload URL" required :error="errors.payloadUrl">
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
                    <p class="text-muted mt-1 text-xs">{{ createForm.events.length }} selected</p>
                </UFormField>
                <USwitch v-model="createForm.active" label="Active" />
            </form>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Webhook"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ webhookToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
