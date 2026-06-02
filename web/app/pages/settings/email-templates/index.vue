<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'

definePageMeta({ title: 'Email Templates' })
useHead({ title: 'Email Templates — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()

interface EmailTemplateResponse {
    id: string
    name: string
    subject: string
    content: string
    predefined: boolean
    createdAt: string
    updatedAt: string
}

const {
    data: templates,
    pending,
    refresh,
} = await useAsyncData<EmailTemplateResponse[]>('email-templates', () =>
    api<EmailTemplateResponse[]>('/api/settings/email-templates')
)

// ── Create ────────────────────────────────────────────────────────────────
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', subject: '', content: '' })

function openCreate() {
    Object.assign(createForm, { name: '', subject: '', content: '' })
    createOpen.value = true
}

async function submitCreate() {
    creating.value = true
    try {
        await api('/api/settings/email-templates', {
            method: 'POST',
            body: {
                name: createForm.name,
                subject: createForm.subject,
                content: createForm.content,
            },
        })
        toast.add({ title: 'Template created', color: 'success' })
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
const editTarget = ref<EmailTemplateResponse | null>(null)
const editForm = reactive({ name: '', subject: '', content: '' })

function openEdit(t: EmailTemplateResponse) {
    editTarget.value = t
    Object.assign(editForm, { name: t.name, subject: t.subject, content: t.content })
    editOpen.value = true
}

async function submitEdit() {
    if (!editTarget.value) return
    saving.value = true
    try {
        await api(`/api/settings/email-templates/${editTarget.value.id}`, {
            method: 'PUT',
            body: { name: editForm.name, subject: editForm.subject, content: editForm.content },
        })
        toast.add({ title: 'Template saved', color: 'success' })
        editOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

// ── Delete ────────────────────────────────────────────────────────────────
const deleteOpen = ref(false)
const toDelete = ref<EmailTemplateResponse | null>(null)
const deleting = ref(false)

async function confirmDelete() {
    if (!toDelete.value) return
    deleting.value = true
    try {
        await api(`/api/settings/email-templates/${toDelete.value.id}`, { method: 'DELETE' })
        toast.add({ title: 'Template deleted', color: 'success' })
        deleteOpen.value = false
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

const columns: TableColumn<EmailTemplateResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'subject', header: 'Subject' },
    { id: 'type', header: 'Type' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(t: EmailTemplateResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(t) }],
    ]
    if (!t.predefined) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => {
                    toDelete.value = t
                    deleteOpen.value = true
                },
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-highlighted text-xl font-semibold">Email Templates</h2>
                <p class="text-muted text-sm">Reusable email content for campaigns and quotes</p>
            </div>
            <UButton icon="i-tabler-plus" label="New Template" @click="openCreate" />
        </div>

        <UCard :ui="{ body: 'p-0' }">
            <UTable :data="templates ?? []" :columns="columns" :loading="pending" sticky>
                <template #name-cell="{ row }">
                    <span class="font-medium">{{ row.original.name }}</span>
                </template>
                <template #subject-cell="{ row }">
                    <span class="text-muted text-sm">{{ row.original.subject }}</span>
                </template>
                <template #type-cell="{ row }">
                    <UBadge
                        :label="row.original.predefined ? 'System' : 'Custom'"
                        :color="row.original.predefined ? 'neutral' : 'primary'"
                        variant="soft"
                        size="sm"
                    />
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
                        <UIcon name="i-tabler-mail-off" class="text-muted mx-auto size-10" />
                        <p class="text-muted text-sm">No templates yet</p>
                    </div>
                </template>
            </UTable>
        </UCard>

        <!-- Create modal -->
        <UModal v-model:open="createOpen" :ui="{ content: 'sm:max-w-2xl' }">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">
                            Create Email Template
                        </p></template
                    >
                    <form class="space-y-3" @submit.prevent="submitCreate">
                        <UFormField label="Name" required>
                            <UInput
                                v-model="createForm.name"
                                placeholder="e.g. Welcome Email"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Subject" required>
                            <UInput
                                v-model="createForm.subject"
                                placeholder="Email subject line"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Content" required>
                            <UTextarea
                                v-model="createForm.content"
                                placeholder="Email body (HTML supported)"
                                :rows="10"
                                class="w-full font-mono text-sm"
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
                                :disabled="
                                    !createForm.name || !createForm.subject || !createForm.content
                                "
                                @click="submitCreate"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Edit modal -->
        <UModal v-model:open="editOpen" :ui="{ content: 'sm:max-w-2xl' }">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Edit Email Template</p></template
                    >
                    <form class="space-y-3" @submit.prevent="submitEdit">
                        <UFormField label="Name" required>
                            <UInput v-model="editForm.name" class="w-full" />
                        </UFormField>
                        <UFormField label="Subject" required>
                            <UInput v-model="editForm.subject" class="w-full" />
                        </UFormField>
                        <UFormField label="Content" required>
                            <UTextarea
                                v-model="editForm.content"
                                :rows="10"
                                class="w-full font-mono text-sm"
                            />
                        </UFormField>
                    </form>
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
                                :disabled="!editForm.name || !editForm.subject"
                                @click="submitEdit"
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
                        ><p class="text-highlighted font-semibold">Delete Template</p></template
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
