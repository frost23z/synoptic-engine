<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { GroupResponse } from '~/types/settings'

definePageMeta({ title: 'Groups' })
useHead({ title: 'Groups — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

const {
    data: groups,
    pending,
    refresh,
} = await useAsyncData<GroupResponse[]>('groups', () => api<GroupResponse[]>('/api/groups'))

// ── Create modal ──────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '' })

function openCreate() {
    Object.assign(createForm, { name: '', description: '' })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/groups', {
            method: 'POST',
            body: { name: createForm.name, description: createForm.description || undefined },
        })
        toast.add({ title: 'Group created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to create group',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creating.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<GroupResponse | null>(null)
const deleting = ref(false)

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/groups/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Group deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

const columns: TableColumn<GroupResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'description', header: 'Description' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(g: GroupResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDelete.value = g
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
                <h2 class="text-highlighted text-xl font-semibold">Groups</h2>
                <p class="text-muted text-sm">{{ (groups?.length ?? 0).toLocaleString() }} total</p>
            </div>
            <UButton icon="i-tabler-plus" label="New Group" @click="openCreate" />
        </div>

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="groups ?? []" :columns="columns" :loading="pending" sticky>
                <template #name-cell="{ row }">
                    <span class="font-medium">{{ row.original.name }}</span>
                </template>
                <template #description-cell="{ row }">
                    <span class="text-muted text-sm">{{ row.original.description ?? '—' }}</span>
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
                        <UIcon name="i-tabler-users-group" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No groups found</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <!-- Create group modal -->
        <UModal v-model:open="createOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create Group</p>
                    </template>
                    <form class="space-y-3" @submit.prevent="submitCreate">
                        <UFormField label="Name" required>
                            <UInput
                                v-model="createForm.name"
                                placeholder="e.g. Sales Team"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Description">
                            <UInput
                                v-model="createForm.description"
                                placeholder="Optional description"
                                class="w-full"
                            />
                        </UFormField>
                    </form>
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
                                :disabled="!createForm.name"
                                @click="submitCreate"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete modal -->
        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Group</p></template
                    >
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
