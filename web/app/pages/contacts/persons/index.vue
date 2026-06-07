<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { PersonResponse } from '~/types/contacts'

definePageMeta({ title: 'Persons' })
useHead({ title: 'Persons — Synoptic' })

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
        await downloadBlob('/api/persons/export', 'persons.csv')
    } catch {
        toast.add({ title: 'Export failed', color: 'error' })
    } finally {
        exporting.value = false
    }
}

const {
    page,
    search,
    items: persons,
    total,
    pending,
    refresh,
} = await usePaginatedList<PersonResponse>('/api/contacts/persons', { key: 'persons' })

// ── Saved views (datagrid filters) ────────────────────────────────────────
const appliedFilter = computed(() => ({ search: search.value || undefined }))
function applySavedFilter(applied: Record<string, unknown>) {
    search.value = typeof applied.search === 'string' ? applied.search : ''
    page.value = 1
}

const massDeleting = ref(false)
async function massDelete() {
    if (!selected.value.length) return
    massDeleting.value = true
    try {
        await api('/api/contacts/persons/mass-destroy', {
            method: 'POST',
            body: { ids: selected.value },
        })
        toast.add({ title: `${count.value} persons deleted`, color: 'success' })
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
    target: personToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<PersonResponse>({
    endpoint: (p) => `/api/contacts/persons/${p.id}`,
    successMessage: 'Person deleted',
    onDeleted: refresh,
})

const columns: TableColumn<PersonResponse>[] = [
    { accessorKey: 'fullName', header: 'Name' },
    { accessorKey: 'email', header: 'Email' },
    { accessorKey: 'phone', header: 'Phone' },
    { accessorKey: 'jobTitle', header: 'Job Title' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(person: PersonResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                onSelect: () => router.push(`/contacts/persons/${person.id}`),
            },
        ],
    ]
    if (can('contacts.persons.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(person),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Persons" :subtitle="`${total.toLocaleString()} contacts`">
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
                    v-if="can('contacts.persons.create')"
                    icon="i-tabler-plus"
                    label="New Person"
                    to="/contacts/persons/create"
                />
            </template>
        </AppPageHeader>

        <div class="flex flex-wrap items-center gap-3">
            <UInput
                v-model="search"
                placeholder="Search persons…"
                icon="i-tabler-search"
                class="w-64"
            />
            <AppSavedFilters
                src="contacts.persons"
                :applied="appliedFilter"
                @apply="applySavedFilter"
            />
        </div>

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
            :rows="persons"
            :columns="columns"
            :loading="pending"
            selectable
            :selected="selected"
            @update:selected="selectAll"
        >
            <template #fullName-cell="{ row }">
                <NuxtLink
                    :to="`/contacts/persons/${row.original.id}`"
                    class="text-primary font-medium hover:underline"
                >
                    {{ row.original.fullName }}
                </NuxtLink>
            </template>
            <template #email-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.email ?? '—' }}</span>
            </template>
            <template #phone-cell="{ row }">
                <span class="text-muted text-sm">{{ row.original.phone ?? '—' }}</span>
            </template>
            <template #jobTitle-cell="{ row }">
                <span class="text-sm">{{ row.original.jobTitle ?? '—' }}</span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-user-off" message="No persons found">
                    <NuxtLink to="/contacts/persons/create">
                        <UButton size="sm" variant="outline" label="Add first person" />
                    </NuxtLink>
                </AppEmptyState>
            </template>
        </AppListTable>

        <AppPagination v-model:page="page" :total="total" />

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Person"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ personToDelete?.fullName }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
