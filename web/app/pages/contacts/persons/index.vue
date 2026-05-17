<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { PersonResponse, PersonsPage } from '~/types/contacts'

definePageMeta({ title: 'Persons' })
useHead({ title: 'Persons — Synoptic' })

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

const page = ref(1)
const PAGE_SIZE = 20
const search = ref('')
const debouncedSearch = refDebounced(search, 300)

const queryKey = computed(() => ['persons', page.value, debouncedSearch.value])

const {
    data: personsPage,
    pending,
    refresh,
} = await useAsyncData<PersonsPage>(
    () => queryKey.value.join('-'),
    () => {
        const params: Record<string, string | number> = { page: page.value - 1, size: PAGE_SIZE }
        if (debouncedSearch.value) params.q = debouncedSearch.value
        return api<PersonsPage>('/api/contacts/persons', { params })
    },
    { watch: [queryKey] }
)

const persons = computed(() => personsPage.value?.content ?? [])
const total = computed(() => personsPage.value?.totalElements ?? 0)

const columns: TableColumn<PersonResponse>[] = [
    { id: 'select', header: '', meta: { class: { th: 'w-8', td: 'w-8' } } },
    { accessorKey: 'fullName', header: 'Name' },
    { accessorKey: 'email', header: 'Email' },
    { accessorKey: 'phone', header: 'Phone' },
    { accessorKey: 'jobTitle', header: 'Job Title' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(person: PersonResponse) {
    return [
        [
            {
                label: 'View',
                icon: 'i-tabler-eye',
                click: () => router.push(`/contacts/persons/${person.id}`),
            },
        ],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => openDeleteModal(person),
            },
        ],
    ]
}

const deleteOpen = ref(false)
const toDelete = ref<PersonResponse | null>(null)
const deleting = ref(false)

function openDeleteModal(p: PersonResponse) {
    toDelete.value = p
    deleteOpen.value = true
}

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/contacts/persons/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Person deleted', color: 'success' })
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
                <h2 class="text-highlighted text-xl font-semibold">Persons</h2>
                <p class="text-muted text-sm">{{ total.toLocaleString() }} contacts</p>
            </div>
            <NuxtLink v-if="can('contacts.persons.create')" to="/contacts/persons/create">
                <UButton icon="i-tabler-plus" label="New Person" />
            </NuxtLink>
        </div>

        <!-- Search -->
        <UInput
            v-model="search"
            placeholder="Search persons…"
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
            <UTable :data="persons" :columns="columns" :loading="pending" sticky>
                <template #select-header>
                    <UCheckbox
                        :checked="persons.length > 0 && selected.length === persons.length"
                        :indeterminate="selected.length > 0 && selected.length < persons.length"
                        @change="
                            persons.length === selected.length
                                ? clearAll()
                                : selectAll(persons.map((p) => p.id))
                        "
                    />
                </template>
                <template #select-cell="{ row }">
                    <UCheckbox
                        :checked="isSelected(row.original.id)"
                        @change="toggle(row.original.id)"
                    />
                </template>
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
                        <UIcon name="i-tabler-user-off" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No persons found</p>
                        <NuxtLink to="/contacts/persons/create">
                            <UButton size="sm" variant="outline" label="Add first person" />
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
                        <p class="text-highlighted font-semibold">Delete Person</p>
                    </template>
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ toDelete?.fullName }}</strong
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
