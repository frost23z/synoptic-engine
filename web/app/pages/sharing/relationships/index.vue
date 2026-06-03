<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { RelationshipResponse, RelationshipType } from '~/types/sharing'
import {
    RELATIONSHIP_STATUS_COLOR,
    RELATIONSHIP_STATUS_LABEL,
    RELATIONSHIP_TYPE_LABEL,
} from '~/types/sharing'

definePageMeta({ title: 'Relationships' })
useHead({ title: 'Relationships — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const { tenantName, tenantOptions, hasTenantList } = await useTenantNames()

const {
    data: relationships,
    pending,
    refresh,
} = await useAsyncData<RelationshipResponse[]>(
    'relationships',
    () => api<RelationshipResponse[]>('/api/relationships'),
    { default: () => [] }
)

const rows = computed(() => relationships.value ?? [])

// ── Request relationship ───────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const form = reactive<{ targetTenantId: string; type: RelationshipType; note: string }>({
    targetTenantId: '',
    type: 'PARTNER',
    note: '',
})

const typeOptions = (Object.keys(RELATIONSHIP_TYPE_LABEL) as RelationshipType[]).map((t) => ({
    label: RELATIONSHIP_TYPE_LABEL[t],
    value: t,
}))

function openCreate() {
    Object.assign(form, { targetTenantId: '', type: 'PARTNER', note: '' })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/relationships', {
            method: 'POST',
            body: {
                targetTenantId: form.targetTenantId,
                type: form.type,
                note: form.note || undefined,
            },
        })
        toast.add({ title: 'Relationship requested', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to request', description: e?.data?.message, color: 'error' })
    } finally {
        creating.value = false
    }
}

// ── Table ───────────────────────────────────────────────────────────────────
const columns: TableColumn<RelationshipResponse>[] = [
    { id: 'tenants', header: 'Tenants' },
    { accessorKey: 'type', header: 'Type' },
    { accessorKey: 'status', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Requested' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(rel: RelationshipResponse): DropdownMenuItem[][] {
    return [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                onSelect: () => router.push(`/sharing/relationships/${rel.id}`),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Relationships" :subtitle="`${rows.length} total`">
            <template #actions>
                <UButton
                    v-if="can('relationships.manage')"
                    icon="i-tabler-plus"
                    label="Request relationship"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="rows" :columns="columns" :loading="pending">
            <template #tenants-cell="{ row }">
                <NuxtLink
                    :to="`/sharing/relationships/${row.original.id}`"
                    class="text-primary inline-flex items-center gap-1.5 hover:underline"
                >
                    <span>{{ tenantName(row.original.sourceTenantId) }}</span>
                    <UIcon name="i-tabler-arrow-right" class="text-muted size-3.5" />
                    <span>{{ tenantName(row.original.targetTenantId) }}</span>
                </NuxtLink>
            </template>
            <template #type-cell="{ row }">
                <UBadge
                    :label="RELATIONSHIP_TYPE_LABEL[row.original.type]"
                    color="neutral"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #status-cell="{ row }">
                <UBadge
                    :label="RELATIONSHIP_STATUS_LABEL[row.original.status]"
                    :color="RELATIONSHIP_STATUS_COLOR[row.original.status]"
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
                <AppEmptyState
                    icon="i-tabler-arrows-left-right"
                    message="No tenant relationships yet"
                >
                    <UButton
                        v-if="can('relationships.manage')"
                        size="sm"
                        variant="outline"
                        label="Request relationship"
                        @click="openCreate"
                    />
                </AppEmptyState>
            </template>
        </AppListTable>

        <!-- Request relationship modal -->
        <AppConfirmModal
            v-model:open="createOpen"
            title="Request relationship"
            confirm-label="Send request"
            :loading="creating"
            :confirm-disabled="!form.targetTenantId"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Target tenant" required>
                    <USelect
                        v-if="hasTenantList"
                        v-model="form.targetTenantId"
                        :items="tenantOptions"
                        placeholder="Select tenant"
                        class="w-full"
                    />
                    <UInput
                        v-else
                        v-model="form.targetTenantId"
                        placeholder="Tenant ID (UUID)"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Type" required>
                    <USelect v-model="form.type" :items="typeOptions" class="w-full" />
                </UFormField>
                <UFormField label="Note">
                    <UTextarea
                        v-model="form.note"
                        :rows="3"
                        placeholder="Optional message to the other tenant…"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>
    </div>
</template>
