<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'

definePageMeta({ title: 'Email Templates' })
useHead({ title: 'Email Templates — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

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
const editTarget = shallowRef<EmailTemplateResponse | null>(null)
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
const {
    open: deleteOpen,
    target: templateToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<EmailTemplateResponse>({
    endpoint: (t) => `/api/settings/email-templates/${t.id}`,
    successMessage: 'Template deleted',
    onDeleted: refresh,
})

const columns: TableColumn<EmailTemplateResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'subject', header: 'Subject' },
    { id: 'type', header: 'Type' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(t: EmailTemplateResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = []
    if (can('email-templates.edit')) {
        items.push([{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(t) }])
    }
    if (!t.predefined && can('email-templates.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(t),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Email Templates"
            subtitle="Reusable email content for campaigns and quotes"
        >
            <template #actions>
                <UButton
                    v-if="can('email-templates.create')"
                    icon="i-tabler-plus"
                    label="New Template"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="templates ?? []" :columns="columns" :loading="pending">
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
                <AppRowActions
                    v-if="
                        can('email-templates.edit') ||
                        (!row.original.predefined && can('email-templates.delete'))
                    "
                    :items="rowActions(row.original)"
                />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-mail-off" message="No templates yet" />
            </template>
        </AppListTable>

        <!-- Create modal -->
        <AppConfirmModal
            v-model:open="createOpen"
            title="Create Email Template"
            confirm-label="Create"
            :loading="creating"
            :confirm-disabled="!createForm.name || !createForm.subject || !createForm.content"
            width-class="sm:max-w-2xl"
            @confirm="submitCreate"
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
        </AppConfirmModal>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editOpen"
            title="Edit Email Template"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.name || !editForm.subject"
            width-class="sm:max-w-2xl"
            @confirm="submitEdit"
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
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Template"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ templateToDelete?.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
