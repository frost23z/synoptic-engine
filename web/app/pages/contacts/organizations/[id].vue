<script setup lang="ts">
import type { PageResponse } from '~/types/api'
import type { OrganizationResponse } from '~/types/contacts'
import type { ActivityResponse } from '~/types/activities'
import { email, required, url } from '~/utils/validators'

definePageMeta({ title: 'Organization' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const router = useRouter()
const route = useRoute()
const id = route.params.id as string
const shareOpen = ref(false)

const {
    data: org,
    pending: orgPending,
    refresh,
} = await useAsyncData<OrganizationResponse>(`org-${id}`, () =>
    api<OrganizationResponse>(`/api/contacts/organizations/${id}`)
)

const pageTitle = computed(() =>
    org.value?.name ? `${org.value.name} — Synoptic` : 'Organization — Synoptic'
)
useHead({ title: pageTitle })

const activeTab = ref<'details' | 'activities'>('details')

const { data: orgActivities } = await useAsyncData<ActivityResponse[]>(`org-${id}-activities`, () =>
    api<PageResponse<ActivityResponse>>(`/api/contacts/organizations/${id}/activities`).then(
        (p) => p.content
    )
)

// ── Edit ──────────────────────────────────────────────────────────────────
const editing = ref(false)
const {
    submitting: saving,
    errors,
    validate,
    run,
} = useFormSubmit({
    failureTitle: 'Failed to save organization',
})
const editForm = reactive({ name: '', email: '', phone: '', website: '', address: '' })

function openEdit() {
    if (!org.value) return
    Object.assign(editForm, {
        name: org.value.name,
        email: org.value.email ?? '',
        phone: org.value.phone ?? '',
        website: org.value.website ?? '',
        address: org.value.address ?? '',
    })
    editing.value = true
}

function submitEdit() {
    run({
        validate: () =>
            validate(editForm, {
                name: [required('Name is required')],
                email: [email()],
                website: [url()],
            }),
        call: () =>
            api(`/api/contacts/organizations/${id}`, {
                method: 'PUT',
                body: {
                    name: editForm.name,
                    email: editForm.email || undefined,
                    phone: editForm.phone || undefined,
                    website: editForm.website || undefined,
                    address: editForm.address || undefined,
                },
            }),
        fieldHints: ['email', 'name'],
        onSuccess: () => {
            toast.add({ title: 'Saved', color: 'success' })
            editing.value = false
            refresh()
        },
    })
}

// ── Delete ────────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<OrganizationResponse>({
    endpoint: (o) => `/api/contacts/organizations/${o.id}`,
    successMessage: 'Organization deleted',
    onDeleted: () => {
        router.push('/contacts/organizations')
    },
})
</script>

<template>
    <AppDetailLayout v-if="org" to="/contacts/organizations" :title="org.name">
        <template #subtitle>
            <p v-if="org.website" class="text-muted text-sm">{{ org.website }}</p>
        </template>
        <template #actions>
            <UButton
                v-if="can('records.share')"
                icon="i-tabler-share"
                label="Share"
                color="neutral"
                variant="outline"
                @click="shareOpen = true"
            />
            <UButton
                v-if="can('contacts.organizations.edit')"
                icon="i-tabler-pencil"
                label="Edit"
                color="neutral"
                variant="outline"
                @click="openEdit"
            />
            <UButton
                v-if="can('contacts.organizations.delete')"
                icon="i-tabler-trash"
                color="error"
                variant="outline"
                @click="promptDelete(org)"
            />
        </template>

        <!-- Tab bar -->
        <div class="border-default mb-4 flex gap-4 border-b pb-0">
            <button
                class="pb-2 text-sm font-medium transition-colors"
                :class="
                    activeTab === 'details'
                        ? 'text-primary border-primary border-b-2'
                        : 'text-muted'
                "
                @click="activeTab = 'details'"
            >
                Details
            </button>
            <button
                class="pb-2 text-sm font-medium transition-colors"
                :class="
                    activeTab === 'activities'
                        ? 'text-primary border-primary border-b-2'
                        : 'text-muted'
                "
                @click="activeTab = 'activities'"
            >
                Activities
            </button>
        </div>

        <!-- Details tab -->
        <div v-if="activeTab === 'details'">
            <UCard>
                <template #header><p class="text-highlighted font-semibold">Details</p></template>
                <dl class="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                    <div>
                        <dt class="text-muted">Email</dt>
                        <dd class="text-highlighted mt-0.5">{{ org.email ?? '—' }}</dd>
                    </div>
                    <div>
                        <dt class="text-muted">Phone</dt>
                        <dd class="text-highlighted mt-0.5">{{ org.phone ?? '—' }}</dd>
                    </div>
                    <div>
                        <dt class="text-muted">Website</dt>
                        <dd class="text-highlighted mt-0.5">{{ org.website ?? '—' }}</dd>
                    </div>
                    <div>
                        <dt class="text-muted">Address</dt>
                        <dd class="text-highlighted mt-0.5">{{ org.address ?? '—' }}</dd>
                    </div>
                </dl>
            </UCard>
        </div>

        <!-- Activities tab -->
        <div v-if="activeTab === 'activities'">
            <UCard>
                <template #header
                    ><p class="text-highlighted font-semibold">Activities</p></template
                >
                <EntityTimeline
                    :activities="orgActivities ?? []"
                    empty-message="No activities linked to this organization"
                />
            </UCard>
        </div>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editing"
            title="Edit Organization"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.name"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <UFormField label="Name" required :error="errors.name">
                    <UInput v-model="editForm.name" class="w-full" />
                </UFormField>
                <UFormField label="Email" :error="errors.email">
                    <UInput v-model="editForm.email" type="email" class="w-full" />
                </UFormField>
                <UFormField label="Phone">
                    <UInput v-model="editForm.phone" class="w-full" />
                </UFormField>
                <UFormField label="Website" :error="errors.website">
                    <UInput v-model="editForm.website" placeholder="https://..." class="w-full" />
                </UFormField>
                <UFormField label="Address">
                    <UInput v-model="editForm.address" class="w-full" />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Organization"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ org.name }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>

        <ShareRecordModal
            v-if="can('records.share')"
            v-model:open="shareOpen"
            resource-type="organizations"
            :resource-id="id"
            :resource-label="org.name"
        />
    </AppDetailLayout>

    <div v-else-if="orgPending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-32 w-full" />
    </div>
</template>
