<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { WebhookDeliveryRunResponse, WebhookResponse } from '~/types/settings'

definePageMeta({ title: 'Webhook' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const route = useRoute()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const id = route.params.id as string

const canManage = computed(() => can('automations.edit'))

const KNOWN_EVENTS = [
    'lead.created',
    'lead.updated',
    'lead.stage.changed',
    'person.created',
    'quote.created',
]

const {
    data: webhook,
    pending,
    refresh,
} = await useAsyncData<WebhookResponse>(`webhook-${id}`, () =>
    api<WebhookResponse>(`/api/settings/webhooks/${id}`)
)

useHead({
    title: computed(() =>
        webhook.value?.name ? `${webhook.value.name} — Synoptic` : 'Webhook — Synoptic'
    ),
})

// ── Delivery history ──────────────────────────────────────────────────────
const {
    page,
    items: deliveries,
    total: totalDeliveries,
    pending: deliveriesPending,
    refresh: refreshDeliveries,
} = await usePaginatedList<WebhookDeliveryRunResponse>(`/api/settings/webhooks/${id}/deliveries`, {
    key: `webhook-${id}-deliveries`,
})

const deliveryColumns: TableColumn<WebhookDeliveryRunResponse>[] = [
    { accessorKey: 'createdAt', header: 'When' },
    { accessorKey: 'eventName', header: 'Event' },
    { accessorKey: 'status', header: 'Status' },
    { accessorKey: 'responseCode', header: 'Code' },
    { accessorKey: 'errorMessage', header: 'Detail' },
]

// ── Test ──────────────────────────────────────────────────────────────────
const testing = ref(false)
async function test() {
    testing.value = true
    try {
        const run = await api<WebhookDeliveryRunResponse>(`/api/settings/webhooks/${id}/test`, {
            method: 'POST',
        })
        if (run.status === 'SUCCESS') {
            toast.add({
                title: 'Test delivered',
                description: run.responseCode ? `HTTP ${run.responseCode}` : undefined,
                color: 'success',
            })
        } else {
            toast.add({
                title: 'Test delivery failed',
                description: run.errorMessage ?? `HTTP ${run.responseCode ?? '—'}`,
                color: 'error',
            })
        }
        refreshDeliveries()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to send test', description: e?.data?.message, color: 'error' })
    } finally {
        testing.value = false
    }
}

// ── Edit ─────────────────────────────────────────────────────────────────────
const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
    name: '',
    payloadUrl: '',
    events: [] as string[],
    secret: '',
    isActive: true,
})

function toggleEvent(event: string) {
    const i = editForm.events.indexOf(event)
    if (i === -1) editForm.events.push(event)
    else editForm.events.splice(i, 1)
}

function openEdit() {
    if (!webhook.value) return
    Object.assign(editForm, {
        name: webhook.value.name,
        payloadUrl: webhook.value.payloadUrl,
        events: [...webhook.value.events],
        secret: '',
        isActive: webhook.value.isActive,
    })
    editOpen.value = true
}

async function submitEdit() {
    saving.value = true
    try {
        await api(`/api/settings/webhooks/${id}`, {
            method: 'PUT',
            body: {
                name: editForm.name,
                payloadUrl: editForm.payloadUrl,
                // Backend replaces the secret on every update — a blank value clears it.
                secret: editForm.secret || null,
                events: editForm.events,
                isActive: editForm.isActive,
            },
        })
        toast.add({ title: 'Webhook saved', color: 'success' })
        editOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ─────────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<WebhookResponse>({
    endpoint: (w) => `/api/settings/webhooks/${w.id}`,
    successMessage: 'Webhook deleted',
    onDeleted: () => {
        router.push('/settings/webhooks')
    },
})
</script>

<template>
    <AppDetailLayout
        v-if="webhook"
        to="/settings/webhooks"
        :title="webhook.name"
        :subtitle="webhook.payloadUrl"
    >
        <template #actions>
            <UButton
                v-if="canManage"
                icon="i-tabler-send"
                label="Test"
                color="neutral"
                variant="outline"
                :loading="testing"
                @click="test"
            />
            <UButton
                v-if="canManage"
                icon="i-tabler-pencil"
                label="Edit"
                color="neutral"
                variant="outline"
                @click="openEdit"
            />
            <UButton
                v-if="canManage"
                icon="i-tabler-trash"
                color="error"
                variant="outline"
                @click="promptDelete(webhook)"
            />
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div class="space-y-6 lg:col-span-2">
                <!-- Delivery history -->
                <UCard :ui="{ body: 'p-0' }">
                    <template #header>
                        <p class="text-highlighted font-semibold">
                            Delivery history
                            <span class="text-muted font-normal">({{ totalDeliveries }})</span>
                        </p>
                    </template>
                    <UTable
                        :data="deliveries"
                        :columns="deliveryColumns"
                        :loading="deliveriesPending"
                    >
                        <template #createdAt-cell="{ row }">
                            <span class="text-muted text-sm">{{
                                formatDate(row.original.createdAt)
                            }}</span>
                        </template>
                        <template #eventName-cell="{ row }">
                            <UBadge
                                :label="row.original.eventName"
                                color="neutral"
                                variant="soft"
                                size="sm"
                            />
                        </template>
                        <template #status-cell="{ row }">
                            <UBadge
                                :label="row.original.status"
                                :color="row.original.status === 'SUCCESS' ? 'success' : 'error'"
                                variant="soft"
                                size="sm"
                            />
                        </template>
                        <template #responseCode-cell="{ row }">
                            <span class="text-muted text-sm">{{
                                row.original.responseCode ?? '—'
                            }}</span>
                        </template>
                        <template #errorMessage-cell="{ row }">
                            <span class="text-error text-xs">{{
                                row.original.errorMessage ?? '—'
                            }}</span>
                        </template>
                        <template #empty>
                            <AppEmptyState
                                icon="i-tabler-history-off"
                                message="No deliveries yet"
                            />
                        </template>
                    </UTable>
                    <div class="p-3">
                        <AppPagination v-model:page="page" :total="totalDeliveries" />
                    </div>
                </UCard>
            </div>

            <!-- Details sidebar -->
            <div class="space-y-6">
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Details</p>
                    </template>
                    <dl class="space-y-4 text-sm">
                        <div class="flex items-center justify-between gap-2">
                            <dt class="text-muted">Status</dt>
                            <dd>
                                <UBadge
                                    :label="webhook.isActive ? 'Active' : 'Inactive'"
                                    :color="webhook.isActive ? 'success' : 'neutral'"
                                    variant="soft"
                                    size="sm"
                                />
                            </dd>
                        </div>
                        <div class="flex items-center justify-between gap-2">
                            <dt class="text-muted">Signing secret</dt>
                            <dd>
                                <UBadge
                                    :label="webhook.hasSecret ? 'Set' : 'None'"
                                    :color="webhook.hasSecret ? 'primary' : 'neutral'"
                                    variant="soft"
                                    size="sm"
                                />
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted mb-1">Subscribed events</dt>
                            <dd class="flex flex-wrap gap-1">
                                <UBadge
                                    v-for="ev in webhook.events"
                                    :key="ev"
                                    :label="ev"
                                    color="neutral"
                                    variant="soft"
                                    size="xs"
                                />
                                <span v-if="!webhook.events.length" class="text-muted">—</span>
                            </dd>
                        </div>
                        <div class="flex items-center justify-between">
                            <dt class="text-muted">Created</dt>
                            <dd class="text-muted">{{ formatDate(webhook.createdAt) }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit Webhook"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.name.trim() || !editForm.payloadUrl.trim()"
            width-class="sm:max-w-2xl"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <UFormField label="Name" required>
                    <UInput v-model="editForm.name" class="w-full" />
                </UFormField>
                <UFormField label="Payload URL" required>
                    <UInput v-model="editForm.payloadUrl" class="w-full" />
                </UFormField>
                <UFormField
                    label="Signing secret"
                    :help="
                        webhook.hasSecret
                            ? 'A secret is set. Re-enter to change it, or leave blank to remove it.'
                            : 'Optional — used to sign delivery payloads.'
                    "
                >
                    <UInput
                        v-model="editForm.secret"
                        type="password"
                        placeholder="••••••••"
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
                                editForm.events.includes(event)
                                    ? 'bg-primary/10 border-primary text-primary'
                                    : ''
                            "
                        >
                            <input
                                type="checkbox"
                                class="hidden"
                                :checked="editForm.events.includes(event)"
                                @change="toggleEvent(event)"
                            />
                            {{ event }}
                        </label>
                    </div>
                </UFormField>
                <USwitch v-model="editForm.isActive" label="Active" />
            </form>
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Webhook"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ webhook.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </AppDetailLayout>

    <div v-else-if="pending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <div class="grid grid-cols-3 gap-6">
            <USkeleton class="col-span-2 h-64 w-full" />
            <USkeleton class="h-40 w-full" />
        </div>
    </div>
</template>
