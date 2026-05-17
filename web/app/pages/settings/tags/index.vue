<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { TagResponse } from '~/types/leads'

definePageMeta({ title: 'Tags' })
useHead({ title: 'Tags — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

const {
    data: tags,
    pending,
    refresh,
} = await useAsyncData<TagResponse[]>('tags', () => api<TagResponse[]>('/api/tags'))

const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', color: '#6366f1' })

function openCreate() {
    Object.assign(createForm, { name: '', color: '#6366f1' })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/tags', {
            method: 'POST',
            body: { name: createForm.name, color: createForm.color },
        })
        toast.add({ title: 'Tag created', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to create tag', description: e?.data?.message, color: 'error' })
    } finally {
        creating.value = false
    }
}

const deleteOpen = ref(false)
const toDelete = ref<TagResponse | null>(null)
const deleting = ref(false)

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/tags/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Tag deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

const columns: TableColumn<TagResponse>[] = [
    { id: 'tag', header: 'Tag' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(t: TagResponse) {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error' as const,
                click: () => {
                    toDelete.value = t
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
                <h2 class="text-highlighted text-xl font-semibold">Tags</h2>
                <p class="text-muted text-sm">{{ (tags?.length ?? 0).toLocaleString() }} total</p>
            </div>
            <UButton icon="i-tabler-plus" label="New Tag" @click="openCreate" />
        </div>

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="tags ?? []" :columns="columns" :loading="pending" sticky>
                <template #tag-cell="{ row }">
                    <div class="flex items-center gap-2">
                        <span
                            class="size-3 rounded-full"
                            :style="{ backgroundColor: row.original.color ?? '#888' }"
                        />
                        <span class="font-medium">{{ row.original.name }}</span>
                    </div>
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
                        <UIcon name="i-tabler-tag-off" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No tags yet</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <UModal v-model:open="createOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <p class="text-highlighted font-semibold">Create Tag</p>
                    </template>
                    <form class="space-y-3" @submit.prevent="submitCreate">
                        <UFormField label="Name" required>
                            <UInput
                                v-model="createForm.name"
                                placeholder="e.g. Hot Lead"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Color">
                            <div class="flex items-center gap-2">
                                <input
                                    v-model="createForm.color"
                                    type="color"
                                    class="border-default h-9 w-12 cursor-pointer rounded-md border"
                                />
                                <UInput
                                    v-model="createForm.color"
                                    placeholder="#6366f1"
                                    class="flex-1"
                                />
                            </div>
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

        <UModal v-model:open="deleteOpen">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Delete Tag</p></template
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
