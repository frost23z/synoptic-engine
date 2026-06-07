<script setup lang="ts">
import type { MailFolder, EmailResponse, EmailsPage } from '~/types/mail'

definePageMeta({ title: 'Mail' })
useHead({ title: 'Mail — Synoptic' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const { formatRelativeDate } = useFormatters()
const router = useRouter()

const FOLDERS: { key: MailFolder; label: string; icon: string }[] = [
    { key: 'inbox', label: 'Inbox', icon: 'i-tabler-inbox' },
    { key: 'sent', label: 'Sent', icon: 'i-tabler-send' },
    { key: 'draft', label: 'Drafts', icon: 'i-tabler-file-text' },
    { key: 'trash', label: 'Trash', icon: 'i-tabler-trash' },
]

const folder = ref<MailFolder>('inbox')
const page = ref(1)
const PAGE_SIZE = 20

const queryKey = computed(() => ['mail', folder.value, page.value])

const {
    data: emailsPage,
    pending,
    refresh,
} = await useAsyncData<EmailsPage>(
    () => queryKey.value.join('-'),
    () =>
        api<EmailsPage>('/api/mail', {
            params: { folder: folder.value, page: page.value - 1, size: PAGE_SIZE },
        }),
    { watch: [queryKey] }
)

const emails = computed(() => emailsPage.value?.content ?? [])
const total = computed(() => emailsPage.value?.totalElements ?? 0)

function setFolder(f: MailFolder) {
    folder.value = f
    page.value = 1
}

function senderDisplay(email: EmailResponse) {
    const f = email.from
    if (!f) return email.name ?? '—'
    return f.name || f.email || '—'
}

async function markRead(email: EmailResponse, isRead: boolean) {
    try {
        await api(`/api/mail/${email.id}/read`, { method: 'PATCH', body: { isRead } })
        refresh()
    } catch {
        toast.add({ title: 'Failed to update', color: 'error' })
    }
}

async function moveToTrash(email: EmailResponse) {
    try {
        await api(`/api/mail/${email.id}/folder`, { method: 'PATCH', body: { folder: 'trash' } })
        toast.add({ title: 'Moved to trash', color: 'success' })
        refresh()
    } catch {
        toast.add({ title: 'Failed to move', color: 'error' })
    }
}

async function deleteEmail(email: EmailResponse) {
    try {
        await api(`/api/mail/${email.id}`, { method: 'DELETE' })
        toast.add({ title: 'Email deleted', color: 'success' })
        refresh()
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    }
}

// ── Mass ops ──────────────────────────────────────────────────────────────

const { selected, isSelected, toggle, selectAll, clearAll, hasSelection, count } = useMassSelect()
const massDeleting = ref(false)
const massMarkingRead = ref(false)

async function massDelete() {
    if (!hasSelection.value) return
    massDeleting.value = true
    try {
        await api('/api/mail/mass-destroy', {
            method: 'POST',
            body: { ids: selected.value },
        })
        toast.add({ title: `${count.value} emails deleted`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass delete failed', color: 'error' })
    } finally {
        massDeleting.value = false
    }
}

async function massMarkRead() {
    if (!hasSelection.value) return
    massMarkingRead.value = true
    try {
        await api('/api/mail/mass-mark-read', {
            method: 'POST',
            body: { ids: selected.value },
        })
        toast.add({ title: `${count.value} emails marked read`, color: 'success' })
        clearAll()
        refresh()
    } catch {
        toast.add({ title: 'Mass mark read failed', color: 'error' })
    } finally {
        massMarkingRead.value = false
    }
}

// ── Compose modal ─────────────────────────────────────────────────────────
const composeOpen = ref(false)
const sending = ref(false)
const composeForm = reactive({
    to: '',
    subject: '',
    body: '',
    cc: '',
    bcc: '',
})

function openCompose() {
    Object.assign(composeForm, { to: '', subject: '', body: '', cc: '', bcc: '' })
    composeOpen.value = true
}

async function submitCompose() {
    sending.value = true
    try {
        await api('/api/mail', {
            method: 'POST',
            body: {
                to: composeForm.to,
                subject: composeForm.subject,
                body: composeForm.body,
                cc: composeForm.cc
                    ? composeForm.cc
                          .split(',')
                          .map((s) => s.trim())
                          .filter(Boolean)
                    : undefined,
                bcc: composeForm.bcc
                    ? composeForm.bcc
                          .split(',')
                          .map((s) => s.trim())
                          .filter(Boolean)
                    : undefined,
                folders: ['sent'],
            },
        })
        toast.add({ title: 'Email sent', color: 'success' })
        composeOpen.value = false
        if (folder.value === 'sent') refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to send', description: e?.data?.message, color: 'error' })
    } finally {
        sending.value = false
    }
}
</script>

<template>
    <div class="flex h-full gap-0">
        <!-- Folder sidebar -->
        <aside class="border-default flex w-48 shrink-0 flex-col border-r">
            <div v-if="can('mail.edit')" class="p-3">
                <UButton
                    icon="i-tabler-pencil"
                    label="Compose"
                    class="w-full"
                    @click="openCompose"
                />
            </div>
            <nav class="flex-1 space-y-0.5 px-2 pb-3">
                <button
                    v-for="f in FOLDERS"
                    :key="f.key"
                    class="hover:bg-muted flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-colors"
                    :class="
                        folder === f.key ? 'bg-muted text-highlighted font-medium' : 'text-muted'
                    "
                    @click="setFolder(f.key)"
                >
                    <UIcon :name="f.icon" class="size-4 shrink-0" />
                    {{ f.label }}
                </button>
            </nav>
        </aside>

        <!-- Email list -->
        <div class="flex min-w-0 flex-1 flex-col">
            <!-- Header -->
            <div class="border-default flex items-center justify-between border-b px-4 py-3">
                <div class="flex items-center gap-3">
                    <UCheckbox
                        :checked="emails.length > 0 && selected.length === emails.length"
                        :indeterminate="selected.length > 0 && selected.length < emails.length"
                        @change="
                            emails.length === selected.length
                                ? clearAll()
                                : selectAll(emails.map((e) => e.id))
                        "
                    />
                    <h2 class="text-highlighted font-semibold capitalize">{{ folder }}</h2>
                </div>
                <span class="text-muted text-sm">{{ total.toLocaleString() }} messages</span>
            </div>

            <!-- Mass ops action bar -->
            <div
                v-if="hasSelection"
                class="bg-default border-default flex items-center gap-3 border-b px-4 py-2"
            >
                <span class="text-muted text-sm">{{ count }} selected</span>
                <UButton
                    v-if="can('mail.edit')"
                    label="Mark Read"
                    size="sm"
                    color="info"
                    variant="soft"
                    :loading="massMarkingRead"
                    @click="massMarkRead"
                />
                <UButton
                    v-if="can('mail.edit')"
                    icon="i-tabler-trash"
                    label="Delete"
                    size="sm"
                    color="error"
                    variant="soft"
                    :loading="massDeleting"
                    @click="massDelete"
                />
                <UButton
                    label="Clear"
                    size="sm"
                    color="neutral"
                    variant="ghost"
                    @click="clearAll"
                />
            </div>

            <!-- Loading -->
            <div v-if="pending" class="space-y-0">
                <div v-for="i in 8" :key="i" class="border-default border-b px-4 py-3">
                    <USkeleton class="mb-1.5 h-4 w-48" />
                    <USkeleton class="h-3.5 w-72" />
                </div>
            </div>

            <!-- Empty -->
            <div
                v-else-if="emails.length === 0"
                class="flex flex-1 flex-col items-center justify-center gap-2 py-16"
            >
                <UIcon name="i-tabler-inbox-off" class="text-muted size-12" />
                <p class="text-muted text-sm">No messages in {{ folder }}</p>
            </div>

            <!-- List -->
            <ul v-else class="flex-1 overflow-y-auto">
                <li
                    v-for="email in emails"
                    :key="email.id"
                    class="border-default hover:bg-muted/50 group flex cursor-pointer items-start gap-3 border-b px-4 py-3 transition-colors"
                    :class="!email.isRead ? 'bg-primary/5' : ''"
                    @click="router.push(`/mail/${email.id}`)"
                >
                    <!-- Checkbox + unread dot -->
                    <div class="flex shrink-0 flex-col items-center gap-1 pt-0.5" @click.stop>
                        <UCheckbox :checked="isSelected(email.id)" @change="toggle(email.id)" />
                        <div
                            class="size-2 rounded-full"
                            :class="!email.isRead ? 'bg-primary' : 'bg-transparent'"
                        />
                    </div>

                    <div class="min-w-0 flex-1">
                        <div class="flex items-baseline justify-between gap-2">
                            <span
                                class="truncate text-sm"
                                :class="
                                    !email.isRead
                                        ? 'text-highlighted font-semibold'
                                        : 'text-default font-medium'
                                "
                            >
                                {{ senderDisplay(email) }}
                            </span>
                            <span class="text-muted shrink-0 text-xs">{{
                                formatRelativeDate(email.createdAt)
                            }}</span>
                        </div>
                        <p
                            class="truncate text-sm"
                            :class="!email.isRead ? 'text-highlighted' : 'text-muted'"
                        >
                            {{ email.subject || '(no subject)' }}
                        </p>
                    </div>

                    <!-- Row actions (show on hover) -->
                    <div
                        v-if="can('mail.edit')"
                        class="invisible flex shrink-0 items-center gap-1 group-hover:visible"
                        @click.stop
                    >
                        <UTooltip :text="email.isRead ? 'Mark unread' : 'Mark read'">
                            <UButton
                                :icon="email.isRead ? 'i-tabler-mail' : 'i-tabler-mail-opened'"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                @click="markRead(email, !email.isRead)"
                            />
                        </UTooltip>
                        <UTooltip v-if="folder !== 'trash'" text="Move to trash">
                            <UButton
                                icon="i-tabler-trash"
                                color="neutral"
                                variant="ghost"
                                size="xs"
                                @click="moveToTrash(email)"
                            />
                        </UTooltip>
                        <UTooltip v-if="folder === 'trash'" text="Delete permanently">
                            <UButton
                                icon="i-tabler-trash-x"
                                color="error"
                                variant="ghost"
                                size="xs"
                                @click="deleteEmail(email)"
                            />
                        </UTooltip>
                    </div>
                </li>
            </ul>

            <!-- Pagination -->
            <div v-if="total > PAGE_SIZE" class="border-default flex justify-center border-t p-3">
                <UPagination
                    v-model:page="page"
                    :total="total"
                    :items-per-page="PAGE_SIZE"
                    :sibling-count="1"
                    show-edges
                />
            </div>
        </div>

        <!-- Compose modal -->
        <AppConfirmModal
            v-model:open="composeOpen"
            title="New Message"
            confirm-label="Send"
            :loading="sending"
            :confirm-disabled="!composeForm.to"
            width-class="sm:max-w-xl"
            @confirm="submitCompose"
        >
            <form class="space-y-3" @submit.prevent="submitCompose">
                <UFormField label="To" required>
                    <UInput
                        v-model="composeForm.to"
                        placeholder="recipient@example.com"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="CC">
                    <UInput
                        v-model="composeForm.cc"
                        placeholder="cc@example.com, ..."
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Subject">
                    <UInput v-model="composeForm.subject" placeholder="Subject" class="w-full" />
                </UFormField>
                <UFormField label="Message">
                    <UTextarea
                        v-model="composeForm.body"
                        placeholder="Write your message..."
                        :rows="8"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>
    </div>
</template>
