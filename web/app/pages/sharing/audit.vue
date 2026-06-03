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
const { tenantName } = useTenantNames()

// Record-scoped query (resourceType + resourceId): the audit view that does not
// require the caller's own tenant id. The owner/actor self-views are noted in
// NEXT.md (they need tenantId surfaced from /auth/me).
const resourceType = ref<string>('leads')
const resourceId = ref('')
const page = ref(1)

const typeOptions = SHARE_RESOURCE_TYPES.map((t) => ({
    label: RESOURCE_TYPE_LABEL[t] ?? t,
    value: t as string,
}))

const queryKey = computed(() => [
    'cross-tenant-audit',
    resourceType.value,
    resourceId.value,
    page.value,
])

const { data, pending } = await useAsyncData<CrossTenantAuditPage | null>(
    () => queryKey.value.join('|'),
    () => {
        if (!resourceId.value.trim()) return Promise.resolve(null)
        return api<CrossTenantAuditPage>('/api/cross-tenant-audit', {
            params: {
                resourceType: resourceType.value,
                resourceId: resourceId.value.trim(),
                page: page.value - 1,
                size: PAGE_SIZE,
            },
        })
    },
    { watch: [queryKey] }
)

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.totalElements ?? 0)

watch([resourceType, resourceId], () => {
    page.value = 1
})

const columns: TableColumn<CrossTenantAuditDto>[] = [
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
            subtitle="See who accessed a record across the tenant boundary"
        />

        <!-- Record selector -->
        <div class="flex flex-wrap items-end gap-3">
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
        </div>

        <AppListTable v-if="resourceId.trim()" :rows="rows" :columns="columns" :loading="pending">
            <template #action-cell="{ row }">
                <UBadge
                    :label="row.original.action"
                    :color="CROSS_TENANT_ACTION_COLOR[row.original.action]"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #actorTenantId-cell="{ row }">
                <span class="text-default text-sm">{{
                    tenantName(row.original.actorTenantId)
                }}</span>
            </template>
            <template #ownerTenantId-cell="{ row }">
                <span class="text-default text-sm">{{
                    tenantName(row.original.ownerTenantId)
                }}</span>
            </template>
            <template #at-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.at) }}</span>
            </template>
            <template #empty>
                <AppEmptyState
                    icon="i-tabler-history"
                    message="No cross-tenant activity for this record"
                />
            </template>
        </AppListTable>

        <AppEmptyState
            v-else
            icon="i-tabler-history"
            message="Enter a record ID above to view its cross-tenant audit trail"
        />

        <AppPagination v-if="resourceId.trim()" v-model:page="page" :total="total" />
    </div>
</template>
