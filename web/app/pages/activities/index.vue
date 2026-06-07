<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type {
    ActivityResponse,
    ActivityFileResponse,
    ActivityParticipantResponse,
} from '~/types/activities'
import { ACTIVITY_TYPE_COLOR, ACTIVITY_TYPE_ICON } from '~/types/activities'

definePageMeta({ title: 'Activities' })
useHead({ title: 'Activities — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const isDoneFilter = ref<boolean | undefined>(undefined)
const typeFilter = ref<string | undefined>(undefined)

const ACTIVITY_TYPES = ['CALL', 'EMAIL', 'MEETING', 'TASK', 'NOTE', 'MESSAGE']
const typeOptions = [
    { label: 'All types', value: undefined },
    ...ACTIVITY_TYPES.map((t) => ({ label: t.charAt(0) + t.slice(1).toLowerCase(), value: t })),
]
const doneOptions = [
    { label: 'All', value: undefined },
    { label: 'Pending', value: false },
    { label: 'Done', value: true },
]

const {
    page,
    search,
    items: activities,
    total,
    pending,
    refresh,
} = await usePaginatedList<ActivityResponse>('/api/activities', {
    key: 'activities',
    params: () => ({
        isDone: isDoneFilter.value === undefined ? undefined : String(isDoneFilter.value),
        type: typeFilter.value,
    }),
})

// ── Toggle done ──────────────────────────────────────────────────────────
const togglingId = ref<string | null>(null)

async function toggleDone(activity: ActivityResponse) {
    togglingId.value = activity.id
    try {
        await api(`/api/activities/${activity.id}/done`, { method: 'PATCH' })
        refresh()
    } catch {
        toast.add({ title: 'Failed to update activity', color: 'error' })
    } finally {
        togglingId.value = null
    }
}

// ── Delete ───────────────────────────────────────────────────────────────
const {
    open: deleteOpen,
    target: activityToDelete,
    deleting,
    prompt: promptDelete,
    confirm: confirmDelete,
} = useDeleteResource<ActivityResponse>({
    endpoint: (a) => `/api/activities/${a.id}`,
    successMessage: 'Activity deleted',
    onDeleted: refresh,
})

// ── View modal ───────────────────────────────────────────────────────────
const viewOpen = ref(false)
const viewActivity = shallowRef<ActivityResponse | null>(null)

function openView(a: ActivityResponse) {
    viewActivity.value = a
    viewOpen.value = true
    loadFiles(a.id)
    loadParticipants(a.id)
}

watch(viewOpen, (val) => {
    if (!val) {
        activityFiles.value = []
        participants.value = []
        selectedActivityId.value = null
    }
})

// ── Activity files ────────────────────────────────────────────────────────
const { downloadBlob } = useDownload()

const selectedActivityId = ref<string | null>(null)
const activityFiles = ref<ActivityFileResponse[]>([])
const filesPending = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const uploadingFile = ref(false)

async function loadFiles(activityId: string) {
    selectedActivityId.value = activityId
    filesPending.value = true
    try {
        activityFiles.value = await api<ActivityFileResponse[]>(
            `/activityFiles/search/findAllByActivityId?activityId=${activityId}`
        )
    } finally {
        filesPending.value = false
    }
}

async function uploadFile(e: Event) {
    const target = e.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file || !selectedActivityId.value) return
    uploadingFile.value = true
    try {
        const formData = new FormData()
        formData.append('file', file)
        const uploaded = await api<ActivityFileResponse>(
            `/api/activities/${selectedActivityId.value}/file`,
            { method: 'POST', body: formData }
        )
        activityFiles.value.push(uploaded)
        toast.add({ title: 'File uploaded', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to upload file', color: 'error' })
    } finally {
        uploadingFile.value = false
        target.value = ''
    }
}

async function downloadActivityFile(file: ActivityFileResponse) {
    try {
        await downloadBlob(
            `/api/activities/${selectedActivityId.value}/file/${file.id}/download`,
            file.name
        )
    } catch {
        toast.add({ title: 'Failed to download file', color: 'error' })
    }
}

// ── Activity participants ──────────────────────────────────────────────
const participants = ref<ActivityParticipantResponse[]>([])
const participantsPending = ref(false)
const addingParticipant = ref(false)
const participantUserId = ref('')

const { data: allUsers } = await useAsyncData<{ id: string; fullName: string; email: string }[]>(
    'users-for-participants',
    () => api<{ id: string; fullName: string; email: string }[]>('/api/users')
)
const userOptions = computed(
    () => allUsers.value?.map((u) => ({ label: u.fullName, value: u.id })) ?? []
)

/** Resolve a participant's display name/email from the users lookup. */
function participantName(p: ActivityParticipantResponse): string {
    const u = allUsers.value?.find((x) => x.id === p.userId)
    return u?.fullName ?? p.personId ?? p.userId ?? '—'
}
function participantEmail(p: ActivityParticipantResponse): string {
    return allUsers.value?.find((x) => x.id === p.userId)?.email ?? ''
}

async function loadParticipants(activityId: string) {
    participantsPending.value = true
    try {
        const detail = await api<{ participants?: ActivityParticipantResponse[] }>(
            `/api/activities/${activityId}`
        )
        participants.value = detail.participants ?? []
    } catch {
        participants.value = []
    } finally {
        participantsPending.value = false
    }
}

async function addParticipant() {
    if (!selectedActivityId.value || !participantUserId.value) return
    addingParticipant.value = true
    try {
        await api(`/api/activities/${selectedActivityId.value}/participants`, {
            method: 'POST',
            body: { userId: participantUserId.value },
        })
        await loadParticipants(selectedActivityId.value)
        participantUserId.value = ''
        toast.add({ title: 'Participant added', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to add participant', color: 'error' })
    } finally {
        addingParticipant.value = false
    }
}

async function removeParticipant(participant: ActivityParticipantResponse) {
    if (!selectedActivityId.value || !participant.userId) return
    const userId = participant.userId
    try {
        await api(`/api/activities/${selectedActivityId.value}/participants/${userId}`, {
            method: 'DELETE',
        })
        participants.value = participants.value.filter((p) => p.userId !== userId)
        toast.add({ title: 'Participant removed', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to remove participant', color: 'error' })
    }
}

// ── Mass ops ───────────────────────────────────────────────────────────
const { selected: massSelected, selectAll: massSelectAll, clearAll, count } = useMassSelect()
const massDeleting = ref(false)
const massMarkingDone = ref(false)

async function massDelete() {
    if (!massSelected.value.length) return
    massDeleting.value = true
    try {
        await api('/api/activities/mass-destroy', {
            method: 'POST',
            body: { ids: massSelected.value },
        })
        toast.add({ title: `${count.value} activities deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

async function massMarkDone() {
    if (!massSelected.value.length) return
    massMarkingDone.value = true
    try {
        await api('/api/activities/mass-update', {
            method: 'POST',
            body: { ids: massSelected.value, isDone: true },
        })
        toast.add({ title: `${count.value} activities marked done`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass update failed', color: 'error' })
    } finally {
        massMarkingDone.value = false
    }
}

// ── Table columns ────────────────────────────────────────────────────────
const columns: TableColumn<ActivityResponse>[] = [
    { id: 'done', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
    { accessorKey: 'title', header: 'Title' },
    { accessorKey: 'type', header: 'Type' },
    { accessorKey: 'scheduleFrom', header: 'Scheduled' },
    { id: 'actions', header: '', meta: { class: { th: 'w-10', td: 'w-10' } } },
]

function rowActions(a: ActivityResponse): DropdownMenuItem[][] {
    const items: DropdownMenuItem[][] = [
        [
            { label: 'View', icon: 'i-tabler-eye', onSelect: () => openView(a) },
            {
                label: a.isDone ? 'Mark pending' : 'Mark done',
                icon: a.isDone ? 'i-tabler-circle-x' : 'i-tabler-circle-check',
                onSelect: () => toggleDone(a),
            },
        ],
    ]
    if (can('activities.delete')) {
        items.push([
            {
                label: 'Delete',
                icon: 'i-tabler-trash',
                color: 'error',
                onSelect: () => promptDelete(a),
            },
        ])
    }
    return items
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="Activities" :subtitle="`${total.toLocaleString()} total`" />

        <!-- Filters -->
        <div class="flex flex-wrap items-center gap-3">
            <UInput
                v-model="search"
                placeholder="Search activities…"
                icon="i-tabler-search"
                class="w-56"
            />
            <USelect v-model="typeFilter" :items="typeOptions" placeholder="Type" class="w-36" />
            <USelect
                v-model="isDoneFilter"
                :items="doneOptions"
                placeholder="Status"
                class="w-32"
            />
        </div>

        <AppMassActionBar :count="count" @clear="clearAll">
            <UButton
                label="Mark Done"
                size="sm"
                color="success"
                variant="soft"
                :loading="massMarkingDone"
                @click="massMarkDone"
            />
            <UButton
                icon="i-tabler-trash"
                label="Delete"
                size="sm"
                color="error"
                variant="soft"
                :loading="massDeleting"
                @click="massDelete"
            />
        </AppMassActionBar>

        <AppListTable
            :rows="activities"
            :columns="columns"
            :loading="pending"
            selectable
            :selected="massSelected"
            @update:selected="massSelectAll"
        >
            <template #done-cell="{ row }">
                <UButton
                    :icon="row.original.isDone ? 'i-tabler-circle-check-filled' : 'i-tabler-circle'"
                    :color="row.original.isDone ? 'success' : 'neutral'"
                    variant="ghost"
                    size="xs"
                    :loading="togglingId === row.original.id"
                    @click="toggleDone(row.original)"
                />
            </template>
            <template #title-cell="{ row }">
                <span
                    class="text-sm font-medium"
                    :class="row.original.isDone ? 'text-muted line-through' : 'text-highlighted'"
                >
                    {{ row.original.title }}
                </span>
            </template>
            <template #type-cell="{ row }">
                <UBadge
                    :label="row.original.type"
                    :color="ACTIVITY_TYPE_COLOR[row.original.type]"
                    :icon="ACTIVITY_TYPE_ICON[row.original.type]"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #scheduleFrom-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.scheduleFrom) }}</span>
            </template>
            <template #actions-cell="{ row }">
                <AppRowActions :items="rowActions(row.original)" />
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-calendar-off" message="No activities found" />
            </template>
        </AppListTable>

        <AppPagination v-model:page="page" :total="total" />

        <!-- View / Files / Participants modal (bespoke) -->
        <UModal v-model:open="viewOpen">
            <template #content>
                <UCard>
                    <template #header>
                        <div class="flex items-center gap-2">
                            <UIcon
                                :name="
                                    viewActivity
                                        ? ACTIVITY_TYPE_ICON[viewActivity.type]
                                        : 'i-tabler-calendar'
                                "
                                class="text-muted size-4"
                            />
                            <p class="text-highlighted font-semibold">{{ viewActivity?.title }}</p>
                        </div>
                    </template>

                    <div class="space-y-3">
                        <div v-if="viewActivity?.comment" class="text-muted text-sm">
                            {{ viewActivity.comment }}
                        </div>

                        <!-- Activity Files -->
                        <div class="mt-4 space-y-2">
                            <div class="flex items-center justify-between">
                                <p class="text-muted text-xs font-semibold uppercase">Files</p>
                                <div>
                                    <input
                                        ref="fileInput"
                                        type="file"
                                        class="hidden"
                                        @change="uploadFile"
                                    />
                                    <UButton
                                        icon="i-tabler-upload"
                                        size="xs"
                                        color="neutral"
                                        variant="outline"
                                        label="Upload"
                                        :loading="uploadingFile"
                                        @click="fileInput?.click()"
                                    />
                                </div>
                            </div>
                            <div v-if="filesPending" class="space-y-1">
                                <USkeleton v-for="i in 2" :key="i" class="h-8 w-full" />
                            </div>
                            <div
                                v-else-if="activityFiles.length === 0"
                                class="text-muted py-2 text-sm"
                            >
                                No files attached
                            </div>
                            <div
                                v-for="f in activityFiles"
                                :key="f.id"
                                class="border-default flex items-center justify-between rounded-lg border px-3 py-2"
                            >
                                <div class="flex items-center gap-2">
                                    <UIcon name="i-tabler-file" class="text-muted size-4" />
                                    <span class="text-sm">{{ f.name }}</span>
                                </div>
                                <UButton
                                    icon="i-tabler-download"
                                    size="xs"
                                    color="neutral"
                                    variant="ghost"
                                    @click="downloadActivityFile(f)"
                                />
                            </div>
                        </div>

                        <!-- Participants -->
                        <div class="mt-4 space-y-2">
                            <p class="text-muted text-xs font-semibold uppercase">Participants</p>
                            <div class="flex gap-2">
                                <USelect
                                    v-model="participantUserId"
                                    :items="userOptions"
                                    placeholder="Add participant…"
                                    class="flex-1"
                                />
                                <UButton
                                    icon="i-tabler-plus"
                                    size="sm"
                                    :loading="addingParticipant"
                                    :disabled="!participantUserId"
                                    @click="addParticipant"
                                />
                            </div>
                            <div v-if="participantsPending" class="space-y-1">
                                <USkeleton v-for="i in 2" :key="i" class="h-8 w-full" />
                            </div>
                            <div
                                v-for="p in participants"
                                :key="p.id"
                                class="border-default flex items-center justify-between rounded-lg border px-3 py-2"
                            >
                                <div>
                                    <p class="text-sm font-medium">{{ participantName(p) }}</p>
                                    <p class="text-muted text-xs">{{ participantEmail(p) }}</p>
                                </div>
                                <UButton
                                    icon="i-tabler-x"
                                    size="xs"
                                    color="neutral"
                                    variant="ghost"
                                    @click="removeParticipant(p)"
                                />
                            </div>
                        </div>
                    </div>

                    <template #footer>
                        <div class="flex justify-end">
                            <UButton
                                color="neutral"
                                variant="outline"
                                label="Close"
                                @click="viewOpen = false"
                            />
                        </div>
                    </template>
                </UCard>
            </template>
        </UModal>

        <!-- Delete modal -->
        <AppConfirmModal
            v-model:open="deleteOpen"
            title="Delete Activity"
            confirm-label="Delete"
            confirm-color="error"
            :loading="deleting"
            @confirm="confirmDelete"
        >
            <p class="text-muted text-sm">
                Delete <strong class="text-highlighted">{{ activityToDelete?.title }}</strong
                >? This cannot be undone.
            </p>
        </AppConfirmModal>
    </div>
</template>
