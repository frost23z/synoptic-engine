<script setup lang="ts">
import type { AccessLevel, RelationshipResponse, SharePolicyResponse } from '~/types/sharing'
import {
    ACCESS_LEVEL_COLOR,
    ACCESS_LEVEL_LABEL,
    GRANTABLE_ACCESS_LEVELS,
    RELATIONSHIP_STATUS_COLOR,
    RELATIONSHIP_STATUS_LABEL,
    RELATIONSHIP_TYPE_LABEL,
    RESOURCE_TYPE_LABEL,
    SHARE_RESOURCE_TYPES,
} from '~/types/sharing'

definePageMeta({ title: 'Relationship' })

const api = useApi()
const toast = useToast()
const route = useRoute()
const { can } = usePermissions()
const { formatDate } = useFormatters()
const { tenantName } = useTenantNames()
const id = route.params.id as string

const {
    data: rel,
    pending,
    refresh,
} = await useAsyncData<RelationshipResponse>(`relationship-${id}`, () =>
    api<RelationshipResponse>(`/api/relationships/${id}`)
)
useHead({ title: 'Relationship — Synoptic' })

// ── Lifecycle (accept / suspend / resume / revoke) ──────────────────────────
const acting = ref(false)
const revokeOpen = ref(false)

const PAST_TENSE: Record<string, string> = {
    accept: 'accepted',
    suspend: 'suspended',
    resume: 'resumed',
    revoke: 'revoked',
}

async function runLifecycle(action: 'accept' | 'suspend' | 'resume' | 'revoke') {
    acting.value = true
    try {
        await api(`/api/relationships/${id}/${action}`, { method: 'PATCH' })
        toast.add({ title: `Relationship ${PAST_TENSE[action]}`, color: 'success' })
        revokeOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: `Failed to ${action}`, description: e?.data?.message, color: 'error' })
    } finally {
        acting.value = false
    }
}

// ── Share policies ──────────────────────────────────────────────────────────
const { data: policies, refresh: refreshPolicies } = await useAsyncData<SharePolicyResponse[]>(
    `relationship-${id}-policies`,
    () =>
        can('share-policies.view')
            ? api<SharePolicyResponse[]>(`/api/relationships/${id}/policies`)
            : Promise.resolve([]),
    { default: () => [] }
)
const activePolicies = computed(() => (policies.value ?? []).filter((p) => !p.revokedAt))

const policyOpen = ref(false)
const savingPolicy = ref(false)
const policyForm = reactive<{
    resourceType: string
    accessLevel: AccessLevel
    materialize: boolean
    filterJson: string
    cascadeJson: string
}>({
    resourceType: 'leads',
    accessLevel: 'READ',
    materialize: true,
    filterJson: '',
    cascadeJson: '',
})

const resourceTypeOptions = SHARE_RESOURCE_TYPES.map((t) => ({
    label: RESOURCE_TYPE_LABEL[t] ?? t,
    value: t as string,
}))
const accessLevelOptions = GRANTABLE_ACCESS_LEVELS.map((a) => ({
    label: ACCESS_LEVEL_LABEL[a],
    value: a,
}))

function openPolicy() {
    Object.assign(policyForm, {
        resourceType: 'leads',
        accessLevel: 'READ',
        materialize: true,
        filterJson: '',
        cascadeJson: '',
    })
    policyOpen.value = true
}

async function submitPolicy() {
    savingPolicy.value = true
    try {
        await api(`/api/relationships/${id}/policies`, {
            method: 'POST',
            body: {
                resourceType: policyForm.resourceType,
                accessLevel: policyForm.accessLevel,
                materialize: policyForm.materialize,
                filterJson: policyForm.filterJson || undefined,
                cascadeJson: policyForm.cascadeJson || undefined,
            },
        })
        toast.add({ title: 'Policy added', color: 'success' })
        policyOpen.value = false
        refreshPolicies()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to add policy', description: e?.data?.message, color: 'error' })
    } finally {
        savingPolicy.value = false
    }
}

const {
    open: policyDeleteOpen,
    target: policyToRevoke,
    deleting: revokingPolicy,
    prompt: promptRevokePolicy,
    confirm: confirmRevokePolicy,
} = useDeleteResource<SharePolicyResponse>({
    endpoint: (p) => `/api/share-policies/${p.id}`,
    successMessage: 'Policy revoked',
    onDeleted: refreshPolicies,
})
</script>

<template>
    <AppDetailLayout v-if="rel" to="/sharing/relationships" title="Relationship">
        <template #subtitle>
            <div class="mt-0.5 flex items-center gap-2">
                <UBadge
                    :label="RELATIONSHIP_TYPE_LABEL[rel.type]"
                    color="neutral"
                    variant="soft"
                    size="sm"
                />
                <UBadge
                    :label="RELATIONSHIP_STATUS_LABEL[rel.status]"
                    :color="RELATIONSHIP_STATUS_COLOR[rel.status]"
                    variant="soft"
                    size="sm"
                />
            </div>
        </template>
        <template #actions>
            <template v-if="can('relationships.manage')">
                <UButton
                    v-if="rel.status === 'PENDING'"
                    icon="i-tabler-check"
                    label="Accept"
                    color="success"
                    variant="outline"
                    size="sm"
                    :loading="acting"
                    @click="runLifecycle('accept')"
                />
                <UButton
                    v-if="rel.status === 'ACTIVE'"
                    icon="i-tabler-player-pause"
                    label="Suspend"
                    color="neutral"
                    variant="outline"
                    size="sm"
                    :loading="acting"
                    @click="runLifecycle('suspend')"
                />
                <UButton
                    v-if="rel.status === 'SUSPENDED'"
                    icon="i-tabler-player-play"
                    label="Resume"
                    color="success"
                    variant="outline"
                    size="sm"
                    :loading="acting"
                    @click="runLifecycle('resume')"
                />
                <UButton
                    v-if="rel.status !== 'REVOKED'"
                    icon="i-tabler-ban"
                    label="Revoke"
                    color="error"
                    variant="outline"
                    size="sm"
                    @click="revokeOpen = true"
                />
            </template>
        </template>

        <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <!-- Details -->
            <div class="lg:col-span-2">
                <UCard>
                    <template #header
                        ><p class="text-highlighted font-semibold">Details</p></template
                    >
                    <dl class="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                        <div>
                            <dt class="text-muted">Source tenant</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ tenantName(rel.sourceTenantId) }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Target tenant</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ tenantName(rel.targetTenantId) }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Type</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ RELATIONSHIP_TYPE_LABEL[rel.type] }}
                            </dd>
                        </div>
                        <div>
                            <dt class="text-muted">Status</dt>
                            <dd class="text-highlighted mt-0.5">
                                {{ RELATIONSHIP_STATUS_LABEL[rel.status] }}
                            </dd>
                        </div>
                        <div v-if="rel.note" class="col-span-2">
                            <dt class="text-muted">Note</dt>
                            <dd class="text-highlighted mt-0.5">{{ rel.note }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>

            <!-- Timeline / meta -->
            <div>
                <UCard>
                    <template #header><p class="text-highlighted font-semibold">Info</p></template>
                    <dl class="space-y-2 text-sm">
                        <div class="flex justify-between">
                            <dt class="text-muted">Requested</dt>
                            <dd class="text-highlighted">{{ formatDate(rel.createdAt) }}</dd>
                        </div>
                        <div class="flex justify-between">
                            <dt class="text-muted">Accepted</dt>
                            <dd class="text-highlighted">{{ formatDate(rel.acceptedAt) }}</dd>
                        </div>
                        <div v-if="rel.revokedAt" class="flex justify-between">
                            <dt class="text-muted">Revoked</dt>
                            <dd class="text-highlighted">{{ formatDate(rel.revokedAt) }}</dd>
                        </div>
                    </dl>
                </UCard>
            </div>
        </div>

        <!-- Share policies -->
        <UCard v-if="can('share-policies.view')">
            <template #header>
                <div class="flex items-center justify-between">
                    <div>
                        <p class="text-highlighted font-semibold">Share policies</p>
                        <p class="text-muted text-xs">
                            Rules that auto-share matching records across this relationship.
                        </p>
                    </div>
                    <UButton
                        v-if="can('share-policies.manage') && rel.status !== 'REVOKED'"
                        icon="i-tabler-plus"
                        size="xs"
                        color="neutral"
                        variant="outline"
                        label="Add policy"
                        @click="openPolicy"
                    />
                </div>
            </template>

            <div v-if="!activePolicies.length" class="text-muted py-6 text-center text-sm">
                No share policies yet
            </div>
            <ul v-else class="divide-default divide-y">
                <li
                    v-for="policy in activePolicies"
                    :key="policy.id"
                    class="flex items-center justify-between gap-3 py-3"
                >
                    <div class="flex items-center gap-2">
                        <span class="text-highlighted text-sm font-medium">
                            {{ RESOURCE_TYPE_LABEL[policy.resourceType] ?? policy.resourceType }}
                        </span>
                        <UBadge
                            :label="ACCESS_LEVEL_LABEL[policy.accessLevel]"
                            :color="ACCESS_LEVEL_COLOR[policy.accessLevel]"
                            variant="soft"
                            size="xs"
                        />
                        <UBadge
                            v-if="policy.materialize"
                            label="Materialized"
                            color="neutral"
                            variant="soft"
                            size="xs"
                        />
                    </div>
                    <UButton
                        v-if="can('share-policies.manage')"
                        icon="i-tabler-trash"
                        color="error"
                        variant="ghost"
                        size="xs"
                        @click="promptRevokePolicy(policy)"
                    />
                </li>
            </ul>
        </UCard>

        <!-- Revoke relationship modal -->
        <AppConfirmModal
            v-model:open="revokeOpen"
            title="Revoke relationship"
            confirm-label="Revoke"
            confirm-color="error"
            :loading="acting"
            @confirm="runLifecycle('revoke')"
        >
            <p class="text-muted text-sm">
                Revoke this relationship? All share policies and shared access between these tenants
                will stop. This cannot be undone.
            </p>
        </AppConfirmModal>

        <!-- Add policy modal -->
        <AppConfirmModal
            v-model:open="policyOpen"
            title="Add share policy"
            confirm-label="Add policy"
            :loading="savingPolicy"
            @confirm="submitPolicy"
        >
            <form class="space-y-3" @submit.prevent="submitPolicy">
                <div class="grid grid-cols-2 gap-3">
                    <UFormField label="Resource type" required>
                        <USelect
                            v-model="policyForm.resourceType"
                            :items="resourceTypeOptions"
                            class="w-full"
                        />
                    </UFormField>
                    <UFormField label="Access level" required>
                        <USelect
                            v-model="policyForm.accessLevel"
                            :items="accessLevelOptions"
                            class="w-full"
                        />
                    </UFormField>
                </div>
                <USwitch v-model="policyForm.materialize" label="Materialize shared records" />
                <UFormField label="Filter (JSON)" hint="Optional">
                    <UTextarea
                        v-model="policyForm.filterJson"
                        :rows="2"
                        placeholder='e.g. {"status":"won"}'
                        class="w-full font-mono text-xs"
                    />
                </UFormField>
                <UFormField label="Cascade (JSON)" hint="Optional">
                    <UTextarea
                        v-model="policyForm.cascadeJson"
                        :rows="2"
                        placeholder='e.g. {"activities":true}'
                        class="w-full font-mono text-xs"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>

        <!-- Revoke policy modal -->
        <AppConfirmModal
            v-model:open="policyDeleteOpen"
            title="Revoke policy"
            confirm-label="Revoke"
            confirm-color="error"
            :loading="revokingPolicy"
            @confirm="confirmRevokePolicy"
        >
            <p class="text-muted text-sm">
                Revoke the
                <strong class="text-highlighted">{{
                    policyToRevoke
                        ? (RESOURCE_TYPE_LABEL[policyToRevoke.resourceType] ??
                          policyToRevoke.resourceType)
                        : ''
                }}</strong>
                share policy? Records shared by this policy will no longer be shared.
            </p>
        </AppConfirmModal>
    </AppDetailLayout>

    <div v-else-if="pending" class="space-y-4">
        <USkeleton class="h-8 w-64" />
        <div class="grid grid-cols-3 gap-6">
            <USkeleton class="col-span-2 h-40 w-full" />
            <USkeleton class="h-40 w-full" />
        </div>
    </div>
</template>
