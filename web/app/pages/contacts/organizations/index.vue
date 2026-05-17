<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { OrganizationResponse, OrgsPage } from '~/types/contacts'

definePageMeta({ title: 'Organizations' })
useHead({ title: 'Organizations — Synoptic' })

const api = useApi()
const toast = useToast()
const router = useRouter()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const { selected, isSelected, toggle, selectAll, clearAll, hasSelection, count } = useMassSelect()
const massDeleting = ref(false)

async function massDelete() {
    if (!hasSelection.value) return
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

const page = ref(1)
const PAGE_SIZE = 20
const search = ref('')
const debouncedSearch = refDebounced(search, 300)

const queryKey = computed(() => ['orgs', page.value, debouncedSearch.value])

const {
    data: orgsPage,
    pending,
    refresh,
} = await useAsyncData<OrgsPage>(
    () => queryKey.value.join('-'),
    () => {
        const params: Record<string, string | number> = { page: page.value - 1, size: PAGE_SIZE }
        if (debouncedSearch.value) params.q = debouncedSearch.value
        return api<OrgsPage>('/api/contacts/organizations', { params })
    },
    { watch: [queryKey] }
)

const orgs = computed(() => orgsPage.value?.content ?? [])
const total = computed(() => orgsPage.value?.totalElements ?? 0)

const columns: TableColumn<OrganizationResponse>[] = [
    { id: 'select', header: '', meta: { class: { th: 'w-8', td: 'w-8' } } },
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'email', header: 'Email' },
    { accessorKey: 'phone', header: 'Phone' },
    { accessorKey: 'website', header: 'Website' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(org: OrganizationResponse) {
    return [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                click: () => router.push(`/contacts/organizations/${org.id}`),
            },
        ],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => openDeleteModal(org),
            },
        ],
    ]
}

const deleteOpen = ref(false)
const toDelete = ref<OrganizationResponse | null>(null)
const deleting = ref(false)

function openDeleteModal(org: OrganizationResponse) {
    toDelete.value = org
    deleteOpen.value = true
}

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/contacts/organizations/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Organization deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}
</script>

<template>
    <div class="space-y-4">
        <!-- Page header -->
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Organizations</h2>
                <p class="text-muted text-sm">{{ total.toLocaleString() }} organizations</p>
            </div>
            <NuxtLink
                v-if="can('contacts.organizations.create')"
                to="/contacts/organizations/create"
            >
                <UButton icon="i-tabler-plus" label="New Organization" />
            </NuxtLink>
        </div>

        <!-- Search -->
        <UInput
            v-model="search"
            placeholder="Search organizations…"
            icon="i-tabler-search"
            class="w-64"
        />

        <!-- Mass-select action bar -->
        <div
            v-if="hasSelection"
            class="bg-default border-default flex items-center gap-3 rounded-lg border px-4 py-2"
        >
            <span class="text-muted text-sm">{{ count }} selected</span>
            <UButton
                icon="i-tabler-trash"
                label="Delete"
                size="sm"
                color="error"
                variant="soft"
                :loading="massDeleting"
                @click="massDelete"
            />
            <UButton label="Clear" size="sm" color="neutral" variant="ghost" @click="clearAll" />
        </div>

        <!-- Table -->
        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="orgs" :columns="columns" :loading="pending" sticky>
                <template #select-header>
                    <UCheckbox
                        :checked="orgs.length > 0 && selected.length === orgs.length"
                        :indeterminate="selected.length > 0 && selected.length < orgs.length"
                        @change="
                            orgs.length === selected.length
                                ? clearAll()
                                : selectAll(orgs.map((o) => o.id))
                        "
                    />
                </template>
                <template #select-cell="{ row }">
                    <UCheckbox
                        :checked="isSelected(row.original.id)"
                        @change="toggle(row.original.id)"
                    />
                </template>
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
                        <UIcon name="i-tabler-building-off" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No organizations found</p>
                        <NuxtLink to="/contacts/organizations/create">
                            <UButton size="sm" variant="outline" label="Add first organization" />
                        </NuxtLink>
                    </div>
                </template>
            </UTable>
        </UCard>

        <div v-if="total > PAGE_SIZE" class="flex justify-center">
            <UPagination
                v-model:page="page"
                :total="total"
                :items-per-page="PAGE_SIZE"
                :sibling-count="1"
                show-edges
            />
        </div>

        <!-- Delete modal -->
        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Delete Organization</p>
                    </template>
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
