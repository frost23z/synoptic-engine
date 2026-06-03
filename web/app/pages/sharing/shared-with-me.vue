<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { RecordShareResponse } from '~/types/sharing'
import {
    ACCESS_LEVEL_COLOR,
    ACCESS_LEVEL_LABEL,
    RESOURCE_TYPE_LABEL,
    SHARE_RESOURCE_TYPES,
} from '~/types/sharing'

definePageMeta({ title: 'Shared with me' })
useHead({ title: 'Shared with me — Synoptic' })

const api = useApi()
const { formatDate } = useFormatters()
const { tenantName } = await useTenantNames()

const resourceType = ref('')

const { data: shares, pending } = await useAsyncData<RecordShareResponse[]>(
    'shared-with-me',
    () =>
        api<RecordShareResponse[]>('/api/records/shared-with-me', {
            params: resourceType.value ? { resourceType: resourceType.value } : {},
        }),
    { watch: [resourceType], default: () => [] }
)

const rows = computed(() => (shares.value ?? []).filter((s) => !s.revokedAt))

const typeFilterOptions = [
    { label: 'All types', value: '' },
    ...SHARE_RESOURCE_TYPES.map((t) => ({ label: RESOURCE_TYPE_LABEL[t] ?? t, value: t })),
]

const columns: TableColumn<RecordShareResponse>[] = [
    { id: 'resource', header: 'Resource' },
    { accessorKey: 'ownerTenantId', header: 'Owner tenant' },
    { accessorKey: 'accessLevel', header: 'Access' },
    { accessorKey: 'expiresAt', header: 'Expires' },
    { accessorKey: 'createdAt', header: 'Shared' },
]
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Shared with me" :subtitle="`${rows.length} record(s)`" />

        <div class="flex flex-wrap items-center gap-3">
            <USelect v-model="resourceType" :items="typeFilterOptions" class="w-44" />
        </div>

        <AppListTable :rows="rows" :columns="columns" :loading="pending">
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
            <template #ownerTenantId-cell="{ row }">
                <span class="text-default text-sm">{{
                    tenantName(row.original.ownerTenantId)
                }}</span>
            </template>
            <template #accessLevel-cell="{ row }">
                <UBadge
                    :label="ACCESS_LEVEL_LABEL[row.original.accessLevel]"
                    :color="ACCESS_LEVEL_COLOR[row.original.accessLevel]"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #expiresAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.expiresAt) }}</span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #empty>
                <AppEmptyState
                    icon="i-tabler-share-off"
                    message="No records have been shared with you"
                />
            </template>
        </AppListTable>
    </div>
</template>
