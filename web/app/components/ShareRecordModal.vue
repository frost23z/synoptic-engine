<script setup lang="ts">
import type { AccessLevel, RecordShareResponse } from '~/types/sharing'
import { ACCESS_LEVEL_COLOR, ACCESS_LEVEL_LABEL, GRANTABLE_ACCESS_LEVELS } from '~/types/sharing'

/**
 * Manage cross-tenant sharing for a single record: lists the tenants it's
 * currently shared with (revocable) and a form to share it with another tenant.
 * Gate the trigger + this component on `records.share`.
 */
const props = defineProps<{
    open: boolean
    /** Plural resource path, e.g. "leads", "quotes". */
    resourceType: string
    resourceId: string
    /** Optional record label for the dialog title. */
    resourceLabel?: string
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { tenantName, tenantOptions, hasTenantList } = useTenantNames()

const shares = ref<RecordShareResponse[]>([])
const loading = ref(false)
const adding = ref(false)

const form = reactive<{
    consumerTenantId: string
    accessLevel: AccessLevel
    expiresAt: string
    note: string
}>({
    consumerTenantId: '',
    accessLevel: 'READ',
    expiresAt: '',
    note: '',
})

const accessLevelOptions = GRANTABLE_ACCESS_LEVELS.map((a) => ({
    label: ACCESS_LEVEL_LABEL[a],
    value: a,
}))
const activeShares = computed(() => shares.value.filter((s) => !s.revokedAt))

function resetForm() {
    Object.assign(form, { consumerTenantId: '', accessLevel: 'READ', expiresAt: '', note: '' })
}

async function loadShares() {
    loading.value = true
    try {
        shares.value = await api<RecordShareResponse[]>(
            `/api/records/${props.resourceType}/${props.resourceId}/shares`
        )
    } catch {
        shares.value = []
    } finally {
        loading.value = false
    }
}

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            resetForm()
            loadShares()
        }
    }
)

async function addShare() {
    adding.value = true
    try {
        await api('/api/records/share', {
            method: 'POST',
            body: {
                consumerTenantId: form.consumerTenantId,
                resourceType: props.resourceType,
                resourceId: props.resourceId,
                accessLevel: form.accessLevel,
                expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : undefined,
                note: form.note || undefined,
            },
        })
        toast.add({ title: 'Record shared', color: 'success' })
        resetForm()
        loadShares()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to share', description: e?.data?.message, color: 'error' })
    } finally {
        adding.value = false
    }
}

async function revokeShare(share: RecordShareResponse) {
    try {
        await api(`/api/records/share/${share.id}`, { method: 'DELETE' })
        toast.add({ title: 'Share revoked', color: 'success' })
        loadShares()
    } catch {
        toast.add({ title: 'Failed to revoke', color: 'error' })
    }
}
</script>

<template>
    <AppConfirmModal
        :open="open"
        :title="resourceLabel ? `Share “${resourceLabel}”` : 'Share record'"
        confirm-label="Share"
        :loading="adding"
        :confirm-disabled="!form.consumerTenantId"
        width-class="sm:max-w-2xl"
        @update:open="emit('update:open', $event)"
        @confirm="addShare"
    >
        <div class="space-y-4">
            <!-- Existing shares -->
            <div>
                <p class="text-muted mb-2 text-xs font-semibold uppercase">Shared with</p>
                <div v-if="loading" class="space-y-2">
                    <USkeleton class="h-8 w-full" />
                    <USkeleton class="h-8 w-full" />
                </div>
                <div v-else-if="!activeShares.length" class="text-muted text-sm">
                    Not shared with any tenant yet
                </div>
                <ul v-else class="divide-default divide-y">
                    <li
                        v-for="share in activeShares"
                        :key="share.id"
                        class="flex items-center justify-between gap-3 py-2"
                    >
                        <div class="flex flex-wrap items-center gap-2">
                            <span class="text-highlighted text-sm font-medium">
                                {{ tenantName(share.consumerTenantId) }}
                            </span>
                            <UBadge
                                :label="ACCESS_LEVEL_LABEL[share.accessLevel]"
                                :color="ACCESS_LEVEL_COLOR[share.accessLevel]"
                                variant="soft"
                                size="xs"
                            />
                            <span v-if="share.expiresAt" class="text-muted text-xs">
                                expires {{ formatDate(share.expiresAt) }}
                            </span>
                        </div>
                        <UButton
                            icon="i-tabler-trash"
                            color="error"
                            variant="ghost"
                            size="xs"
                            @click="revokeShare(share)"
                        />
                    </li>
                </ul>
            </div>

            <!-- Add form -->
            <form class="border-default space-y-3 border-t pt-4" @submit.prevent="addShare">
                <p class="text-muted text-xs font-semibold uppercase">Share with a tenant</p>
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Tenant" required>
                        <USelect
                            v-if="hasTenantList"
                            v-model="form.consumerTenantId"
                            :items="tenantOptions"
                            placeholder="Select tenant"
                            class="w-full"
                        />
                        <UInput
                            v-else
                            v-model="form.consumerTenantId"
                            placeholder="Tenant ID (UUID)"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Access level" required>
                        <USelect
                            v-model="form.accessLevel"
                            :items="accessLevelOptions"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <UFormField label="Expires" hint="Optional">
                    <UInput v-model="form.expiresAt" type="date" class="w-full" />
                </UFormField>
                <UFormField label="Note" hint="Optional">
                    <UInput v-model="form.note" class="w-full" />
                </UFormField>
            </form>
        </div>
    </AppConfirmModal>
</template>
