<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { CrossTenantAuditDto, CrossTenantAuditPage } from '~/types/sharing'
import {
    CROSS_TENANT_ACTION_COLOR,
    RESOURCE_TYPE_LABEL,
    SHARE_RESOURCE_TYPES,
} from '~/types/sharing'

definePageMeta({ title: 'Cross-tenant Audit' })
useHead({ title: 'Cross-tenant Audit — Synoptic' })

const api = useApi()
const { formatDate } = useFormatters()
const { tenantName, isSelf, sessionTenantId } = useTenantNames()

// The three views the backend supports (CrossTenantAuditController):
//  - 'owned'    → ownerTenantId=self: who reached into records we own (default)
//  - 'activity' → actorTenantId=self: everything we did across the border
//  - 'record'   → resourceType+resourceId: the trail for one specific record
type AuditScope = 'owned' | 'activity' | 'record'
const scope = ref<AuditScope>('owned')
const scopeOptions = [
    { label: 'Records I own', value: 'owned' },
    { label: 'My cross-tenant activity', value: 'activity' },
    { label: 'A specific record', value: 'record' },
]

const resourceType = ref<string>('leads')
const resourceId = ref('')
const page = ref(1)

const typeOptions = SHARE_RESOURCE_TYPES.map((t) => ({
    label: RESOURCE_TYPE_LABEL[t] ?? t,
    value: t as string,
}))

// Record scope needs a record id; the self scopes need the session tenant.
const ready = computed(() =>
    scope.value === 'record' ? !!resourceId.value.trim() : !!sessionTenantId.value
)

const queryKey = computed(() => [
    'cross-tenant-audit',
    scope.value,
    resourceType.value,
    resourceId.value,
    sessionTenantId.value ?? '',
    page.value,
])

const { data, pending } = await useAsyncData<CrossTenantAuditPage | null>(
    () => queryKey.value.join('|'),
    () => {
        if (scope.value === 'record') {
            if (!resourceId.value.trim()) return Promise.resolve(null)
            return api<CrossTenantAuditPage>('/api/cross-tenant-audit', {
                params: {
                    resourceType: resourceType.value,
                    resourceId: resourceId.value.trim(),
                    page: page.value - 1,
                    size: PAGE_SIZE,
                },
            })
        }
        if (!sessionTenantId.value) return Promise.resolve(null)
        const tenantParam =
            scope.value === 'owned'
                ? { ownerTenantId: sessionTenantId.value }
                : { actorTenantId: sessionTenantId.value }
        return api<CrossTenantAuditPage>('/api/cross-tenant-audit', {
            params: { ...tenantParam, page: page.value - 1, size: PAGE_SIZE },
        })
    },
    { watch: [queryKey] }
)

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.totalElements ?? 0)

watch([scope, resourceType, resourceId], () => {
    page.value = 1
})

const columns: TableColumn<CrossTenantAuditDto>[] = [
    { id: 'resource', header: 'Resource' },
    { accessorKey: 'action', header: 'Action' },
    { accessorKey: 'actorTenantId', header: 'Actor tenant' },
    { accessorKey: 'ownerTenantId', header: 'Owner tenant' },
    { accessorKey: 'at', header: 'When' },
]
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Cross-tenant Audit"
            subtitle="Who reached across the tenant boundary — and what you reached into"
        />

        <!-- Scope selector -->
        <div class="flex flex-wrap items-end gap-3">
            <UFormField label="View">
                <USelect v-model="scope" :items="scopeOptions" class="w-56" />
            </UFormField>
            <template v-if="scope === 'record'">
                <UFormField label="Resource type">
                    <USelect v-model="resourceType" :items="typeOptions" class="w-44" />
                </UFormField>
                <UFormField label="Resource ID" class="flex-1">
                    <UInput
                        v-model="resourceId"
                        placeholder="Paste a record ID (UUID) to view its cross-tenant history"
                        icon="i-tabler-search"
                        class="w-full"
                    />
                </UFormField>
            </template>
        </div>

        <AppEmptyState
            v-if="scope === 'record' && !resourceId.trim()"
            icon="i-tabler-history"
            message="Enter a record ID above to view its cross-tenant audit trail"
        />
        <template v-else>
            <AppListTable :rows="rows" :columns="columns" :loading="pending || !ready">
                <template #resource-cell="{ row }">
                    <div class="flex items-center gap-2">
                        <UBadge
                            :label="
                                RESOURCE_TYPE_LABEL[row.original.resourceType] ??
                                row.original.resourceType
                            "
                            color="neutral"
                            variant="soft"
                            size="xs"
                        />
                        <span class="text-muted font-mono text-xs">
                            {{ row.original.resourceId.slice(0, 8) }}…
                        </span>
                    </div>
                </template>
                <template #action-cell="{ row }">
                    <UBadge
                        :label="row.original.action"
                        :color="CROSS_TENANT_ACTION_COLOR[row.original.action]"
                        variant="soft"
                        size="sm"
                    />
                </template>
                <template #actorTenantId-cell="{ row }">
                    <span
                        class="text-default text-sm"
                        :class="{ 'font-semibold': isSelf(row.original.actorTenantId) }"
                    >
                        {{
                            isSelf(row.original.actorTenantId)
                                ? 'You'
                                : tenantName(row.original.actorTenantId)
                        }}
                    </span>
                </template>
                <template #ownerTenantId-cell="{ row }">
                    <span
                        class="text-default text-sm"
                        :class="{ 'font-semibold': isSelf(row.original.ownerTenantId) }"
                    >
                        {{
                            isSelf(row.original.ownerTenantId)
                                ? 'You'
                                : tenantName(row.original.ownerTenantId)
                        }}
                    </span>
                </template>
                <template #at-cell="{ row }">
                    <span class="text-muted text-sm">{{ formatDate(row.original.at) }}</span>
                </template>
                <template #empty>
                    <AppEmptyState
                        icon="i-tabler-history"
                        message="No cross-tenant activity to show"
                    />
                </template>
            </AppListTable>

            <AppPagination v-model:page="page" :total="total" />
        </template>
    </div>
</template>
