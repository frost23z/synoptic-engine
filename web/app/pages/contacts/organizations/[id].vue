<script setup lang="ts">
import type { OrganizationResponse } from '~/types/contacts'
import type { ActivityResponse } from '~/types/activities'
import { ACTIVITY_TYPE_ICON } from '~/types/activities'

definePageMeta({ title: 'Organization' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const router = useRouter()
const route = useRoute()
const id = route.params.id as string

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
    api<ActivityResponse[]>(`/api/contacts/organizations/${id}/activities`)
)

// ── Edit ──────────────────────────────────────────────────────────────────
const editing = ref(false)
const saving = ref(false)
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

async function submitEdit() {
    saving.value = true
    try {
        await api(`/api/contacts/organizations/${id}`, {
            method: 'PUT',
            body: {
                name: editForm.name,
                email: editForm.email || undefined,
                phone: editForm.phone || undefined,
                website: editForm.website || undefined,
                address: editForm.address || undefined,
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
const deleteOpen = ref(false)
const deleting = ref(false)

async function confirmDelete() {
    deleting.value = true
    try {
        await api(`/api/contacts/organizations/${id}`, { method: 'DELETE' })
        toast.add({ title: 'Organization deleted', color: 'success' })
        router.push('/contacts/organizations')
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}
</script>

<template>
    <div v-if="org" class="space-y-6">
        <!-- Header -->
        <div class="flex items-start justify-between">
            <div class="flex items-center gap-3">
                <UButton
                    icon="i-tabler-arrow-left"
                    color="neutral"
                    variant="ghost"
                    to="/contacts/organizations"
                />
                <div>
                    <h2 class="text-highlighted text-xl font-semibold">{{ org.name }}</h2>
                    <p v-if="org.website" class="text-muted text-sm">{{ org.website }}</p>
                </div>
            </div>
            <div class="flex gap-2">
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
                    @click="deleteOpen = true"
                />
            </div>
        </div>

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
        <div v-if="activeTab === 'activities'" class="space-y-2">
            <div v-if="!orgActivities?.length" class="text-muted py-8 text-center text-sm">
                No activities linked to this organization
            </div>
            <div
                v-for="act in orgActivities"
                :key="act.id"
                class="border-default rounded-lg border p-3"
            >
                <div class="flex items-center gap-2">
                    <UIcon :name="ACTIVITY_TYPE_ICON[act.type]" class="text-muted size-4" />
                    <p class="text-sm font-medium">{{ act.title }}</p>
                    <UBadge v-if="act.done" label="Done" color="success" variant="soft" size="xs" />
                </div>
                <p v-if="act.comment" class="text-muted mt-1 text-xs">{{ act.comment }}</p>
            </div>
        </div>

        <!-- Edit modal -->
        <UModal v-model:open="editing">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Edit Organization</p></template
                    >
                    <form class="space-y-3" @submit.prevent="submitEdit">
                        <UFormField label="Name" required>
                            <UInput v-model="editForm.name" class="w-full" />
                        </UFormField>
                        <UFormField label="Email">
                            <UInput v-model="editForm.email" type="email" class="w-full" />
                        </UFormField>
                        <UFormField label="Phone">
                            <UInput v-model="editForm.phone" class="w-full" />
                        </UFormField>
                        <UFormField label="Website">
                            <UInput
                                v-model="editForm.website"
                                placeholder="https://..."
                                class="w-full"
                            />
                        </UFormField>
                        <UFormField label="Address">
                            <UInput v-model="editForm.address" class="w-full" />
                        </UFormField>
                    </form>
                    <template #footer>
                        <div class="flex justify-end gap-2">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Cancel"
                                @click="editing = false"
                            />
                            <UButton
                                label="Save"
                                :loading="saving"
                                :disabled="!editForm.name"
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
                        ><p class="text-highlighted font-semibold">Delete Organization</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ org.name }}</strong
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

    <div v-else-if="orgPending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-32 w-full" />
    </div>
</template>
