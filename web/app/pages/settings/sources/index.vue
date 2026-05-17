<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'

definePageMeta({ title: 'Lead Sources' })
useHead({ title: 'Lead Sources — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

interface LeadSourceResponse {
    id: string
    name: string
    createdAt: string
}

const {
    data: sources,
    pending,
    refresh,
} = await useAsyncData<LeadSourceResponse[]>('lead-sources', () =>
    api<LeadSourceResponse[]>('/api/lead-sources')
)

// ── Create ────────────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createName = ref('')

function openCreate() {
    createName.value = ''
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/lead-sources', { method: 'POST', body: { name: createName.value } })
        toast.add({ title: 'Source created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create', description: e?.data?.message, color: 'error' })
    } finally {
        creating.value = false
    }
}

// ── Edit ──────────────────────────────────────────────────────────────────
const editOpen = ref(false)
const saving = ref(false)
const editTarget = ref<LeadSourceResponse | null>(null)
const editName = ref('')

function openEdit(s: LeadSourceResponse) {
    editTarget.value = s
    editName.value = s.name
    editOpen.value = true
}

async function submitEdit() {
    if (!editTarget.value) return
    saving.value = true
    try {
        await api(`/api/lead-sources/${editTarget.value.id}`, {
            method: 'PUT',
            body: { name: editName.value },
        })
        toast.add({ title: 'Source updated', color: 'success' })
        editOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to update', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<LeadSourceResponse | null>(null)
const deleting = ref(false)

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/lead-sources/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Source deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

const columns: TableColumn<LeadSourceResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(s: LeadSourceResponse) {
    return [
        [{ label: 'Edit', icon: 'i-tabler-pencil', click: () => openEdit(s) }],
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDelete.value = s
                    deleteOpen.value = true
                },
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Lead Sources</h2>
                <p class="text-muted text-sm">
                    Where leads originate (website, referral, cold call…)
                </p>
            </div>
            <UButton icon="i-tabler-plus" label="New Source" @click="openCreate" />
        </div>

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="sources ?? []" :columns="columns" :loading="pending" sticky>
                <template #name-cell="{ row }">
                    <span class="font-medium">{{ row.original.name }}</span>
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
                        <UIcon name="i-tabler-source-code" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No lead sources yet</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <UModal v-model:open="createOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">New Lead Source</p></template
                    >
                    <UFormField label="Name" required>
                        <UInput
                            v-model="createName"
                            placeholder="e.g. Website, Cold Call, Referral"
                            class="w-full"
                            @keydown.enter="submitCreate"
                        />
                    </UFormField>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="createOpen = false"
                            />
                            <UButton
                                label="Create"
                                :loading="creating"
                                :disabled="!createName.trim()"
                                @click="submitCreate"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <UModal v-model:open="editOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Edit Lead Source</p></template
                    >
                    <UFormField label="Name" required>
                        <UInput v-model="editName" class="w-full" @keydown.enter="submitEdit" />
                    </UFormField>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="editOpen = false"
                            />
                            <UButton
                                label="Save"
                                :loading="saving"
                                :disabled="!editName.trim()"
                                @click="submitEdit"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Source</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ toDelete?.name }}</strong
                        >? Leads using this source will be unlinked.
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
