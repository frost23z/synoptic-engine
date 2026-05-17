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
const deleteOpen = ref(false)
const deleting = ref(false)

async function confirmDelete() {
    deleting.value = true
    try {
        await api(`/api/contacts/persons/${id}`, { method: 'DELETE' })
        toast.add({ title: 'Person deleted', color: 'success' })
        router.push('/contacts/persons')
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Tags ──────────────────────────────────────────────────────────────────
const { data: allTags } = await useAsyncData<TagResponse[]>('tags-lookup', () =>
    api<TagResponse[]>('/api/tags')
)
const tagSearch = ref('')
const addingTag = ref(false)

const filteredTags = computed(() => {
    const existing = new Set(person.value?.tags.map((t) => t.id) ?? [])
    return (allTags.value ?? []).filter(
        (t) => !existing.has(t.id) && t.name.toLowerCase().includes(tagSearch.value.toLowerCase())
    )
})

async function addTag(tag: TagResponse) {
    addingTag.value = true
    try {
        await api(`/api/contacts/persons/${id}/tags`, { method: 'POST', body: { tagId: tag.id } })
        refresh()
    } catch {
        toast.add({ title: 'Failed to add tag', color: 'error' })
    } finally {
        addingTag.value = false
    }
}

async function removeTag(tagId: string) {
    try {
        await api(`/api/contacts/persons/${id}/tags/${tagId}`, { method: 'DELETE' })
        refresh()
    } catch {
        toast.add({ title: 'Failed to remove tag', color: 'error' })
    }
}
</script>

<template>
    <div v-if="person" class="space-y-6">
        <!-- Header -->
        <div class="flex items-start justify-between">
            <div class="flex items-center gap-3">
                <UButton
                    icon="i-tabler-arrow-left"
                    color="neutral"
                    variant="ghost"
                    to="/contacts/persons"
                />
                <div>
                    <h2 class="text-highlighted text-xl font-semibold">{{ person.fullName }}</h2>
                    <p class="text-muted text-sm">
                        {{ person.jobTitle ?? 'No title'
                        }}<span v-if="orgName"> · {{ orgName }}</span>
                    </p>
                </div>
            </div>
            <div class="flex gap-2">
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
                    @click="deleteOpen = true"
                />
            </div>
        </div>

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
                <UCard>
                    <template #header><p class="text-highlighted font-semibold">Tags</p></template>
                    <div class="space-y-3">
                        <div class="flex flex-wrap gap-1.5">
                            <span
                                v-for="tag in person.tags"
                                :key="tag.id"
                                class="border-default flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium"
                                :style="{ borderColor: tag.color, color: tag.color }"
                            >
                                {{ tag.name }}
                                <button class="hover:opacity-70" @click="removeTag(tag.id)">
                                    <UIcon name="i-tabler-x" class="size-3" />
                                </button>
                            </span>
                            <span v-if="!person.tags.length" class="text-muted text-xs"
                                >No tags</span
                            >
                        </div>
                        <UInput
                            v-model="tagSearch"
                            placeholder="Search tags…"
                            size="sm"
                            icon="i-tabler-search"
                        />
                        <div class="max-h-40 space-y-1 overflow-y-auto">
                            <button
                                v-for="tag in filteredTags"
                                :key="tag.id"
                                class="hover:bg-muted flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs transition-colors"
                                :disabled="addingTag"
                                @click="addTag(tag)"
                            >
                                <span
                                    class="size-2 rounded-full"
                                    :style="{ backgroundColor: tag.color ?? '#888' }"
                                />
                                {{ tag.name }}
                            </button>
                        </div>
                    </div>
                </UCard>
            </div>
        </div>

        <!-- Edit modal -->
        <UModal v-model:open="editing">
            <template #content>
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Edit Person</p></template
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
                                :disabled="!editForm.firstName || !editForm.lastName"
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
                        ><p class="text-highlighted font-semibold">Delete Person</p></template
                    >
                    <p class="text-muted text-sm">
                        Delete <strong class="text-highlighted">{{ person.fullName }}</strong
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

    <div v-else-if="personPending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <USkeleton class="h-40 w-full" />
        <USkeleton class="h-32 w-full" />
    </div>
</template>
