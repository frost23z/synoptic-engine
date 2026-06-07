<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import { required } from '~/utils/validators'

definePageMeta({ title: 'Web Forms' })
useHead({ title: 'Web Forms — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()
const {
    submitting: formSaving,
    errors,
    run,
    validate,
    clearErrors,
} = useFormSubmit({
    failureTitle: 'Failed to save web form',
})

interface AttributeResponse {
    id: string
    code: string
    adminName: string
    type: string
    entityType: string
}

interface WebFormField {
    attributeId: string
    sortOrder: number
    required: boolean
}

interface WebFormFieldResponse extends WebFormField {
    id: string
}

interface WebFormResponse {
    id: string
    title: string
    description?: string
    fields: WebFormFieldResponse[]
    active: boolean
    createdAt: string
    updatedAt: string
}

const {
    data: forms,
    pending,
    refresh,
} = await useAsyncData<WebFormResponse[]>('web-forms', () =>
    api<WebFormResponse[]>('/api/settings/web-forms')
)

const { data: attributes } = await useAsyncData<AttributeResponse[]>('web-form-attributes', () =>
    api<AttributeResponse[]>('/api/settings/attributes')
)

const attributeOptions = computed(() =>
    (attributes.value ?? []).map((a) => ({
        label: `${a.adminName} (${a.entityType})`,
        value: a.id,
    }))
)

// ── Create / Edit shared form ─────────────────────────────────────────────
type FormMode = 'create' | 'edit'
const formOpen = ref(false)
const formMode = ref<FormMode>('create')
const formTarget = shallowRef<WebFormResponse | null>(null)
const formData = reactive({ title: '', description: '', active: true })
const formFields = ref<WebFormField[]>([])

function openCreate() {
    clearErrors()
    formMode.value = 'create'
    formTarget.value = null
    Object.assign(formData, { title: '', description: '', active: true })
    formFields.value = []
    formOpen.value = true
}

function openEdit(w: WebFormResponse) {
    clearErrors()
    formMode.value = 'edit'
    formTarget.value = w
    Object.assign(formData, { title: w.title, description: w.description ?? '', active: w.active })
    formFields.value = w.fields.map((f) => ({
        attributeId: f.attributeId,
        sortOrder: f.sortOrder,
        required: f.required,
    }))
    formOpen.value = true
}

function addField() {
    formFields.value.push({ attributeId: '', sortOrder: formFields.value.length, required: false })
}

function removeField(i: number) {
    formFields.value.splice(i, 1)
    formFields.value.forEach((f, idx) => {
        f.sortOrder = idx
    })
}

function submitForm() {
    const body = {
        title: formData.title,
        description: formData.description || undefined,
        active: formData.active,
        fields: formFields.value
            .filter((f) => f.attributeId)
            .map((f, i) => ({
                attributeId: f.attributeId,
                sortOrder: i,
                required: f.required,
            })),
    }
    run({
        validate: () => validate(formData, { title: [required('Title is required')] }),
        call: () =>
            formMode.value === 'create'
                ? api('/api/settings/web-forms', { method: 'POST', body })
                : api(`/api/settings/web-forms/${formTarget.value!.id}`, { method: 'PUT', body }),
        fieldHints: ['title'],
        onSuccess: () => {
            toast.add({
                title: formMode.value === 'create' ? 'Web form created' : 'Web form saved',
                color: 'success',
            })
            formOpen.value = false
            refresh()
        },
    })
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: formToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<WebFormResponse>({
    endpoint: (w) => `/api/settings/web-forms/${w.id}`,
    successMessage: 'Form deleted',
    onDeleted: refresh,
})

const columns: TableColumn<WebFormResponse>[] = [
    { accessorKey: 'title', header: 'Title' },
    { id: 'fields', header: 'Fields' },
    { id: 'status', header: 'Status' },
    { accessorKey: 'createdAt', header: 'Created' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(w: WebFormResponse): DropdownMenuItem[][] {
    const groups: DropdownMenuItem[][] = []
    if (can('web-forms.edit')) {
        groups.push([{ label: 'Edit', icon: 'i-tabler-pencil', onSelect: () => openEdit(w) }])
    }
    if (can('web-forms.delete')) {
        groups.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(w),
            },
        ])
    }
    return groups
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Web Forms" subtitle="Embeddable forms linked to CRM attributes">
            <template #actions>
                <UButton
                    v-if="can('web-forms.create')"
                    icon="i-tabler-plus"
                    label="New Form"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="forms ?? []" :columns="columns" :loading="pending">
            <template #title-cell="{ row }">
                <div>
                    <p class="text-highlighted font-medium">{{ row.original.title }}</p>
                    <p v-if="row.original.description" class="text-muted text-xs">
                        {{ row.original.description }}
                    </p>
                </div>
            </template>
            <template #fields-cell="{ row }">
                <UBadge
                    :label="`${row.original.fields.length} field${row.original.fields.length === 1 ? '' : 's'}`"
                    color="neutral"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #status-cell="{ row }">
                <UBadge
                    :label="row.original.active ? 'Active' : 'Inactive'"
                    :color="row.original.active ? 'success' : 'neutral'"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions
                    v-if="can('web-forms.edit') || can('web-forms.delete')"
                    :items="rowActions(row.original)"
                />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-forms" message="No web forms yet" />
            </template>
        </AppListTable>

        <!-- Create / Edit modal -->
        <AppConfirmModal
            v-model:open="formOpen"
            :title="`${formMode === 'create' ? 'Create' : 'Edit'} Web Form`"
            :confirm-label="formMode === 'create' ? 'Create' : 'Save'"
            :loading="formSaving"
            :confirm-disabled="!formData.title"
            width-class="sm:max-w-2xl"
            @confirm="submitForm"
        >
            <form class="space-y-4" @submit.prevent="submitForm">
                <UFormField label="Title" required :error="errors.title">
                    <UInput
                        v-model="formData.title"
                        placeholder="e.g. Lead Capture Form"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Description">
                    <UInput v-model="formData.description" placeholder="Optional" class="w-full" />
                </UFormField>
                <USwitch v-model="formData.active" label="Active" />

                <!-- Fields builder -->
                <div class="space-y-2">
                    <div class="flex items-center justify-between">
                        <p class="text-highlighted text-sm font-semibold">Fields</p>
                        <UButton
                            icon="i-tabler-plus"
                            size="xs"
                            color="neutral"
                            variant="outline"
                            label="Add field"
                            @click="addField"
                        />
                    </div>
                    <div
                        v-if="formFields.length === 0"
                        class="text-muted border-default rounded-lg border py-4 text-center text-sm"
                    >
                        No fields added yet
                    </div>
                    <div
                        v-for="(field, i) in formFields"
                        :key="i"
                        class="border-default grid grid-cols-12 items-end gap-2 rounded-lg border p-3"
                    >
                        <UFormField label="Attribute" class="col-span-7">
                            <USelect
                                v-model="field.attributeId"
                                :items="attributeOptions"
                                placeholder="Select attribute"
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Sort" class="col-span-2">
                            <UInput
                                v-model.number="field.sortOrder"
                                type="number"
                                min="0"
                                class="w-full"
                            />
                        </UFormField>
                        <div class="col-span-2 flex flex-col gap-1 pb-1">
                            <label class="text-muted text-xs">Required</label>
                            <USwitch v-model="field.required" />
                        </div>
                        <div class="col-span-1 pb-1">
                            <UButton
                                icon="i-tabler-trash"
                                color="error"
                                variant="ghost"
                                size="xs"
                                @click="removeField(i)"
                            />
                        </div>
                    </div>
                </div>
            </form>
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Web Form"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ formToDelete?.title }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
