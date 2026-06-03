<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { TagResponse } from '~/types/leads'

definePageMeta({ title: 'Tags' })
useHead({ title: 'Tags — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const {
    data: tags,
    pending,
    refresh,
} = await useAsyncData<TagResponse[]>('tags', () => api<TagResponse[]>('/api/tags'))

// ── Create ──────────────────────────────────────────────────────────────
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

// ── Delete ──────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: tagToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<TagResponse>({
    endpoint: (t) => `/api/tags/${t.id}`,
    successMessage: 'Tag deleted',
    onDeleted: refresh,
})

const columns: TableColumn<TagResponse>[] = [
    { id: 'tag', header: 'Tag' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(t: TagResponse): DropdownMenuItem[][] {
    return [
        [
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(t),
            },
        ],
    ]
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Tags" :subtitle="`${(tags?.length ?? 0).toLocaleString()} total`">
            <template #actions>
                <UButton
                    v-if="can('tags.create')"
                    icon="i-tabler-plus"
                    label="New Tag"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="tags ?? []" :columns="columns" :loading="pending">
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
                <AppRowActions v-if="can('tags.delete')" :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-tag-off" message="No tags yet" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Tag"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.name"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <UFormField label="Name" required>
                    <UInput v-model="createForm.name" placeholder="e.g. Hot Lead" class="w-full" />
                </UFormField>
                <UFormField label="Color">
                    <div class="flex items-center gap-2">
                        <input
                            v-model="createForm.color"
                            type="color"
                            class="border-default h-9 w-12 cursor-pointer rounded-md border"
                        />
                        <UInput v-model="createForm.color" placeholder="#6366f1" class="flex-1" />
                    </div>
                </UFormField>
            </form>
        </AppConfirmModal>

        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Tag"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ tagToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
