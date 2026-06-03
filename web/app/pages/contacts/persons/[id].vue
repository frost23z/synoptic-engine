<script setup lang="ts">
import type { TagResponse } from '~/types/leads'
import type { OrganizationResponse, PersonResponse } from '~/types/contacts'

definePageMeta({ title: 'Person' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const router = useRouter()
const route = useRoute()
const id = route.params.id as string
const shareOpen = ref(false)

const {
    data: person,
    pending: personPending,
    refresh,
} = await useAsyncData<PersonResponse>(`person-${id}`, () =>
    api<PersonResponse>(`/api/contacts/persons/${id}`)
)

const pageTitle = computed(() =>
    person.value?.fullName ? `${person.value.fullName} — Synoptic` : 'Person — Synoptic'
)
useHead({ title: pageTitle })

const { data: orgs } = await useAsyncData<OrganizationResponse[]>('orgs-lookup', () =>
    api<OrganizationResponse[]>('/api/contacts/organizations')
)
const orgOptions = computed(() => orgs.value?.map((o) => ({ label: o.name, value: o.id })) ?? [])
const orgName = computed(() => orgs.value?.find((o) => o.id === person.value?.organizationId)?.name)

// ── Edit ──────────────────────────────────────────────────────────────────
const editing = ref(false)
const saving = ref(false)
const editForm = reactive({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    jobTitle: '',
    organizationId: '',
})

function openEdit() {
    if (!person.value) return
    Object.assign(editForm, {
        firstName: person.value.firstName,
        lastName: person.value.lastName,
        email: person.value.email ?? '',
        phone: person.value.phone ?? '',
        jobTitle: person.value.jobTitle ?? '',
        organizationId: person.value.organizationId ?? '',
    })
    editing.value = true
}

async function submitEdit() {
    saving.value = true
    try {
        await api(`/api/contacts/persons/${id}`, {
            method: 'PUT',
            body: {
                firstName: editForm.firstName,
                lastName: editForm.lastName,
                email: editForm.email || undefined,
                phone: editForm.phone || undefined,
                jobTitle: editForm.jobTitle || undefined,
                organizationId: editForm.organizationId || undefined,
            },
        })
        toast.add({ title: 'Saved', color: 'success' })
        editing.value = false
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
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<PersonResponse>({
    endpoint: (p) => `/api/contacts/persons/${p.id}`,
    successMessage: 'Person deleted',
    onDeleted: () => {
        router.push('/contacts/persons')
    },
})

// ── Tags ──────────────────────────────────────────────────────────────────
const { data: allTags } = await useAsyncData<TagResponse[]>('tags-lookup', () =>
    api<TagResponse[]>('/api/tags')
)
</script>

<template>
    <AppDetailLayout v-if="person" to="/contacts/persons" :title="person.fullName">
        <template #subtitle>
            <p class="text-muted text-sm">
                {{ person.jobTitle ?? 'No title' }}<span v-if="orgName"> · {{ orgName }}</span>
            </p>
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
                v-if="can('contacts.persons.edit')"
                icon="i-tabler-pencil"
                label="Edit"
                color="neutral"
                variant="outline"
                @click="openEdit"
            />
            <UButton
                v-if="can('contacts.persons.delete')"
                icon="i-tabler-trash"
                color="error"
                variant="outline"
                @click="promptDelete(person)"
            />
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <!-- Details -->
            <div class="space-y-4 lg:col-span-2">
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Details</p></template
                    >
                    <dl class="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                        <div>
                            <dt class="text-muted">Email</dt>
                            <dd class="text-highlighted mt-0.5">{{ person.email ?? '—' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted">Phone</dt>
                            <dd class="text-highlighted mt-0.5">{{ person.phone ?? '—' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted">Job Title</dt>
                            <dd class="text-highlighted mt-0.5">{{ person.jobTitle ?? '—' }}</dd>
                        </div>
                        <div>
                            <dt class="text-muted">Organization</dt>
                            <dd class="text-highlighted mt-0.5">{{ orgName ?? '—' }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>

            <!-- Tags sidebar -->
            <div>
                <AppTagManager
                    :tags="person.tags"
                    :all-tags="allTags ?? []"
                    :endpoint="`/api/contacts/persons/${id}/tags`"
                    :can-edit="can('contacts.persons.edit')"
                    @changed="refresh"
                />
            </div>
        </div>

        <!-- Edit modal -->
        <AppConfirmModal
            v-model:open="editing"
            title="Edit Person"
            confirm-label="Save"
            :loading="saving"
            :confirm-disabled="!editForm.firstName || !editForm.lastName"
            @confirm="submitEdit"
        >
            <form class="space-y-3" @submit.prevent="submitEdit">
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="First name" required>
                        <UInput v-model="editForm.firstName" class="w-full" />
                    </UFormField>
                    <UFormField label="Last name" required>
                        <UInput v-model="editForm.lastName" class="w-full" />
                    </UFormField>
                </div>
                <UFormField label="Email">
                    <UInput v-model="editForm.email" type="email" class="w-full" />
                </UFormField>
                <UFormField label="Phone">
                    <UInput v-model="editForm.phone" class="w-full" />
                </UFormField>
                <UFormField label="Job title">
                    <UInput v-model="editForm.jobTitle" class="w-full" />
                </UFormField>
                <UFormField label="Organization">
                    <USelect
                        v-model="editForm.organizationId"
                        :items="orgOptions"
                        placeholder="Select organization"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Person"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ person.fullName }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>

        <ShareRecordModal
            v-if="can('records.share')"
            v-model:open="shareOpen"
            resource-type="persons"
            :resource-id="id"
            :resource-label="person.fullName"
        />
    </AppDetailLayout>

    <div v-else-if="personPending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-32 w-full" />
    </div>
</template>
