<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { OrganizationResponse } from '~/types/contacts'

definePageMeta({ title: 'Organizations' })
useHead({ title: 'Organizations — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatDate } = useFormatters()
const { can } = usePermissions()
const { downloadBlob } = useDownload()

const { selected, selectAll, clearAll, count } = useMassSelect()

const exporting = ref(false)
async function exportCsv() {
    exporting.value = true
    try {
        await downloadBlob('/api/organizations/export', 'organizations.csv')
    } catch {
        toast.add({ title: 'Export failed', color: 'error' })
    } finally {
        exporting.value = false
    }
}

const {
    page,
    search,
    items: orgs,
    total,
    pending,
    refresh,
} = await usePaginatedList<OrganizationResponse>('/api/contacts/organizations', { key: 'orgs' })

const massDeleting = ref(false)
async function massDelete() {
    if (!selected.value.length) return
    massDeleting.value = true
    try {
        await api('/api/contacts/organizations/mass-destroy', {
            method: 'POST',
            body: { ids: selected.value },
        })
        toast.add({ title: `${count.value} organizations deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

const {
    open: deleteOpen,
    target: orgToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<OrganizationResponse>({
    endpoint: (o) => `/api/contacts/organizations/${o.id}`,
    successMessage: 'Organization deleted',
    onDeleted: refresh,
})

const columns: TableColumn<OrganizationResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'email', header: 'Email' },
    { accessorKey: 'phone', header: 'Phone' },
    { accessorKey: 'website', header: 'Website' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(org: OrganizationResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                onSelect: () => router.push(`/contacts/organizations/${org.id}`),
            },
        ],
    ]
    if (can('contacts.organizations.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(org),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Organizations" :subtitle="`${total.toLocaleString()} organizations`">
            <template #actions>
                <UButton
                    v-if="can('contacts.view')"
                    icon="i-tabler-download"
                    label="Export"
                    color="neutral"
                    variant="outline"
                    :loading="exporting"
                    @click="exportCsv"
                />
                <UButton
                    v-if="can('contacts.organizations.create')"
                    icon="i-tabler-plus"
                    label="New Organization"
                    to="/contacts/organizations/create"
                />
            </template>
        </AppPageHeader>

        <UInput
            v-model="search"
            placeholder="Search organizations…"
            icon="i-tabler-search"
            class="w-64"
        />

        <AppMassActionBar :count="count" @clear="clearAll">
            <UButton
                icon="i-tabler-trash"
                label="Delete"
                size="sm"
                color="error"
                variant="soft"
                :loading="massDeleting"
                @click="massDelete"
            />
        </AppMassActionBar>

        <AppListTable
            :rows="orgs"
            :columns="columns"
            :loading="pending"
            selectable
            :selected="selected"
            @update:selected="selectAll"
        >
            <template #name-cell="{ row }">
                <NuxtLink
                    :to="`/contacts/organizations/${row.original.id}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.name }}
                </NuxtLink>
            </template>
            <template #email-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.email ?? '—' }}</span>
            </template>
            <template #phone-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.phone ?? '—' }}</span>
            </template>
            <template #website-cell="{ row }">
                <a
                    v-if="row.original.website"
                    :href="row.original.website"
                    target="_blank"
                    rel="noopener"
                    class="text-primary text-sm hover:underline"
                >
                    {{ row.original.website.replace(/^https?:\/\//, '') }}
                </a>
                <span v-else class="text-muted text-sm">—</span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-building-off" message="No organizations found">
                    <NuxtLink to="/contacts/organizations/create">
                        <UButton size="sm" variant="outline" label="Add first organization" />
                    </NuxtLink>
                </AppEmptyState>
            </template>
        </AppListTable>

        <AppPagination v-model:page="page" :total="total" />

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Organization"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ orgToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
