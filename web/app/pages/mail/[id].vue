<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { MailFolder, EmailResponse } from '~/types/mail'

definePageMeta({ title: 'Email' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const route = useRoute()
const router = useRouter()

const id = route.params.id as string

const { data: email, pending } = await useAsyncData<EmailResponse>(`mail-${id}`, () =>
    api<EmailResponse>(`/api/mail/${id}`)
)

const pageTitle = computed(() =>
    email.value?.subject ? `${email.value.subject} — Synoptic` : 'Mail — Synoptic'
)
useHead({ title: pageTitle })

// Mark as read on open
onMounted(async () => {
    if (email.value && !email.value.read && can('mail.edit')) {
        await api(`/api/mail/${id}/read`, { method: 'PATCH', body: { read: true } }).catch(() => {})
    }
})

const FOLDERS: { key: MailFolder; label: string }[] = [
    { key: 'inbox', label: 'Inbox' },
    { key: 'sent', label: 'Sent' },
    { key: 'draft', label: 'Drafts' },
    { key: 'trash', label: 'Trash' },
]

const movingFolder = ref(false)

async function moveToFolder(folder: MailFolder) {
    movingFolder.value = true
    try {
        await api(`/api/mail/${id}/folder`, { method: 'PATCH', body: { folder } })
        toast.add({ title: `Moved to ${folder}`, color: 'success' })
        router.push('/mail')
    } catch {
        toast.add({ title: 'Failed to move', color: 'error' })
    } finally {
        movingFolder.value = false
    }
}

const toggling = ref(false)

async function toggleRead() {
    if (!email.value) return
    toggling.value = true
    try {
        await api(`/api/mail/${id}/read`, { method: 'PATCH', body: { read: !email.value.read } })
        email.value.read = !email.value.read
    } catch {
        toast.add({ title: 'Failed to update', color: 'error' })
    } finally {
        toggling.value = false
    }
}

const deleting = ref(false)

async function deleteEmail() {
    deleting.value = true
    try {
        await api(`/api/mail/${id}`, { method: 'DELETE' })
        toast.add({ title: 'Email deleted', color: 'success' })
        router.push('/mail')
    } catch {
        toast.add({ title: 'Failed to delete', color: 'error' })
    } finally {
        deleting.value = false
    }
}

// ── Reply ─────────────────────────────────────────────────────────────────
const replyOpen = ref(false)
const replying = ref(false)
const replyBody = ref('')

function cancelReply() {
    replyOpen.value = false
    replyBody.value = ''
}

async function submitReply() {
    if (!email.value) return
    replying.value = true
    try {
        const replyTo = email.value.from?.email ?? email.value.replyTo?.[0]?.email ?? ''
        await api('/api/mail', {
            method: 'POST',
            body: {
                to: replyTo,
                subject: `Re: ${email.value.subject}`,
                body: replyBody.value,
                parentId: id,
                folders: ['sent'],
            },
        })
        toast.add({ title: 'Reply sent', color: 'success' })
        replyOpen.value = false
        replyBody.value = ''
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to send', description: e?.data?.message, color: 'error' })
    } finally {
        replying.value = false
    }
}

function addressDisplay(
    a?: { name?: string; email?: string; [key: string]: string | undefined } | null
) {
    if (!a) return '—'
    return a.name ? `${a.name} <${a.email ?? ''}>` : (a.email ?? '—')
}

const moveItems = computed<DropdownMenuItem[]>(() =>
    FOLDERS.filter((f) => !email.value?.folders.includes(f.key)).map((f) => ({
        label: f.label,
        icon: 'i-tabler-folder',
        onSelect: () => moveToFolder(f.key),
    }))
)

const { downloadBlob } = useDownload()

async function downloadAttachment(attachment: { id: string; name: string }) {
    try {
        await downloadBlob(`/api/mail/attachments/${attachment.id}/download`, attachment.name)
    } catch {
        toast.add({ title: 'Failed to download attachment', color: 'error' })
    }
}
</script>

<template>
    <AppDetailLayout to="/mail" root-class="mx-auto max-w-3xl space-y-4 p-6">
        <template #actions>
            <UButton
                v-if="can('mail.edit')"
                :icon="email?.read ? 'i-tabler-mail' : 'i-tabler-mail-opened'"
                :label="email?.read ? 'Mark unread' : 'Mark read'"
                color="neutral"
                variant="outline"
                size="sm"
                :loading="toggling"
                @click="toggleRead"
            />
            <UDropdownMenu v-if="can('mail.edit')" :items="[moveItems]">
                <UButton
                    icon="i-tabler-folder-arrow-right"
                    label="Move"
                    color="neutral"
                    variant="outline"
                    size="sm"
                    :loading="movingFolder"
                />
            </UDropdownMenu>
            <UButton
                v-if="can('mail.edit')"
                icon="i-tabler-trash"
                label="Delete"
                color="error"
                variant="outline"
                size="sm"
                :loading="deleting"
                @click="deleteEmail"
            />
        </template>

        <!-- Loading -->
        <div v-if="pending" class="space-y-3">
            <USkeleton class="h-7 w-3/4" />
            <USkeleton class="h-4 w-48" />
            <USkeleton class="h-4 w-56" />
            <div class="mt-6 space-y-2">
                <USkeleton v-for="i in 6" :key="i" class="h-4 w-full" />
            </div>
        </div>

        <template v-else-if="email">
            <!-- Subject -->
            <h1 class="text-highlighted text-2xl font-semibold">
                {{ email.subject || '(no subject)' }}
            </h1>

            <!-- Meta -->
            <div class="border-default space-y-1.5 rounded-lg border p-4">
                <div class="flex items-start gap-2 text-sm">
                    <span class="text-muted w-12 shrink-0">From</span>
                    <span class="text-highlighted">{{ addressDisplay(email.from) }}</span>
                </div>
                <div v-if="email.cc && email.cc.length > 0" class="flex items-start gap-2 text-sm">
                    <span class="text-muted w-12 shrink-0">CC</span>
                    <span class="text-default">{{ email.cc.map(addressDisplay).join(', ') }}</span>
                </div>
                <div class="flex items-start gap-2 text-sm">
                    <span class="text-muted w-12 shrink-0">Date</span>
                    <span class="text-muted">{{ formatDate(email.createdAt) }}</span>
                </div>
                <div v-if="email.folders.length > 0" class="flex items-start gap-2 text-sm">
                    <span class="text-muted w-12 shrink-0">Folder</span>
                    <div class="flex gap-1">
                        <UBadge
                            v-for="f in email.folders"
                            :key="f"
                            :label="f"
                            color="neutral"
                            variant="soft"
                            size="xs"
                        />
                    </div>
                </div>
            </div>

            <!-- Body -->
            <div class="border-default min-h-48 rounded-lg border p-4">
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div v-if="email.body" class="prose prose-sm max-w-none" v-html="email.body" />
                <p v-else class="text-muted text-sm italic">No content</p>
            </div>

            <!-- Attachments -->
            <div v-if="email.attachments && email.attachments.length > 0" class="mt-4 space-y-1">
                <p class="text-muted text-xs font-semibold uppercase">Attachments</p>
                <div
                    v-for="att in email.attachments"
                    :key="att.id"
                    class="border-default flex items-center justify-between rounded-lg border px-3 py-2"
                >
                    <div class="flex items-center gap-2">
                        <UIcon name="i-tabler-paperclip" class="text-muted size-4" />
                        <span class="text-sm">{{ att.name }}</span>
                        <span v-if="att.size" class="text-muted text-xs">
                            {{ (att.size / 1024).toFixed(1) }} KB
                        </span>
                    </div>
                    <UButton
                        icon="i-tabler-download"
                        size="xs"
                        color="neutral"
                        variant="ghost"
                        @click="downloadAttachment(att)"
                    />
                </div>
            </div>

            <!-- Reply -->
            <div v-if="can('mail.edit') && !replyOpen" class="pt-2">
                <UButton
                    icon="i-tabler-arrow-back"
                    label="Reply"
                    color="neutral"
                    variant="outline"
                    @click="replyOpen = true"
                />
            </div>

            <div
                v-if="can('mail.edit') && replyOpen"
                class="border-default space-y-3 rounded-lg border p-4"
            >
                <p class="text-highlighted text-sm font-semibold">
                    Reply to {{ addressDisplay(email.from) }}
                </p>
                <UTextarea
                    v-model="replyBody"
                    placeholder="Write your reply..."
                    :rows="6"
                    class="w-full"
                />
                <div class="flex justify-end gap-2">
                    <UButton
                        color="neutral"
                        variant="outline"
                        label="Cancel"
                        @click="cancelReply"
                    />
                    <UButton
                        icon="i-tabler-send"
                        label="Send Reply"
                        :loading="replying"
                        :disabled="!replyBody.trim()"
                        @click="submitReply"
                    />
                </div>
            </div>
        </template>

        <!-- Not found -->
        <div v-else class="py-16 text-center">
            <UIcon name="i-tabler-mail-off" class="text-muted mx-auto size-12" />
            <p class="text-muted mt-2 text-sm">Email not found</p>
        </div>
    </AppDetailLayout>
</template>
