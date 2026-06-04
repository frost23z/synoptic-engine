<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { TenantResponse } from '~/types/settings'

definePageMeta({ title: 'Tenants' })
useHead({ title: 'Tenants — Synoptic' })

const api = useApi()
const toast = useToast()
const { formatDate } = useFormatters()
const { can } = usePermissions()

const canManage = computed(() => can('tenants.manage'))

const {
    data: tenants,
    pending,
    refresh,
} = await useAsyncData<TenantResponse[]>('tenants', () => api<TenantResponse[]>('/api/tenants'))

const STATUS_COLOR: Record<string, 'success' | 'warning' | 'error' | 'neutral'> = {
    ACTIVE: 'success',
    SUSPENDED: 'warning',
    ARCHIVED: 'neutral',
    DELETED: 'error',
}
const statusColor = (s: string) => STATUS_COLOR[s] ?? 'neutral'

// ── Provision tenant ─────────────────────────────────────────────────────────
const SLUG_RE = /^[a-z0-9-]+$/
const createOpen = ref(false)
const creating = ref(false)
const slugTouched = ref(false)
const form = reactive({ name: '', slug: '', adminEmail: '', adminPassword: '' })

function slugify(value: string) {
    return value
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
}

// Auto-derive the slug from the name until the user edits it directly.
watch(
    () => form.name,
    (name) => {
        if (!slugTouched.value) form.slug = slugify(name)
    }
)

const slugValid = computed(() => form.slug !== '' && SLUG_RE.test(form.slug))
const canSubmit = computed(
    () =>
        form.name.trim() !== '' &&
        slugValid.value &&
        form.adminEmail.trim() !== '' &&
        form.adminPassword.length >= 8
)

function openCreate() {
    Object.assign(form, { name: '', slug: '', adminEmail: '', adminPassword: '' })
    slugTouched.value = false
    createOpen.value = true
}

async function submitCreate() {
    if (!canSubmit.value) return
    creating.value = true
    try {
        await api('/api/tenants', {
            method: 'POST',
            body: {
                name: form.name,
                slug: form.slug,
                adminEmail: form.adminEmail,
                adminPassword: form.adminPassword,
            },
        })
        toast.add({ title: 'Tenant provisioned', color: 'success' })
        createOpen.value = false
        refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to provision tenant',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        creating.value = false
    }
}

const columns: TableColumn<TenantResponse>[] = [
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'slug', header: 'Slug' },
    { accessorKey: 'status', header: 'Status' },
    { id: 'locale', header: 'Locale' },
    { accessorKey: 'createdAt', header: 'Created' },
]
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader
            title="Tenants"
            :subtitle="`${(tenants?.length ?? 0).toLocaleString()} total`"
        >
            <template #actions>
                <UButton
                    v-if="canManage"
                    icon="i-tabler-plus"
                    label="Provision Tenant"
                    @click="openCreate"
                />
            </template>
        </AppPageHeader>

        <AppListTable :rows="tenants ?? []" :columns="columns" :loading="pending">
            <template #name-cell="{ row }">
                <div>
                    <p class="text-highlighted font-medium">{{ row.original.name }}</p>
                    <p v-if="row.original.legalName" class="text-muted text-xs">
                        {{ row.original.legalName }}
                    </p>
                </div>
            </template>
            <template #slug-cell="{ row }">
                <UBadge :label="row.original.slug" color="neutral" variant="soft" size="sm" />
            </template>
            <template #status-cell="{ row }">
                <UBadge
                    :label="row.original.status"
                    :color="statusColor(row.original.status)"
                    variant="soft"
                    size="sm"
                />
            </template>
            <template #locale-cell="{ row }">
                <span class="text-muted text-sm">
                    {{ row.original.locale ?? '—' }}
                    <template v-if="row.original.timezone">· {{ row.original.timezone }}</template>
                </span>
            </template>
            <template #createdAt-cell="{ row }">
                <span class="text-muted text-sm">{{ formatDate(row.original.createdAt) }}</span>
            </template>
            <template #empty>
                <AppEmptyState icon="i-tabler-building-community" message="No tenants found" />
            </template>
        </AppListTable>

        <AppConfirmModal
            v-model:open="createOpen"
            title="Provision Tenant"
            confirm-label="Provision"
            :loading="creating"
            :confirm-disabled="!canSubmit"
            width-class="sm:max-w-2xl"
            @confirm="submitCreate"
        >
            <form class="space-y-3" @submit.prevent="submitCreate">
                <p class="text-muted text-sm">
                    Creates a new tenant and its initial admin account.
                </p>
                <UFormField label="Name" required>
                    <UInput v-model="form.name" placeholder="Acme Inc." class="w-full" />
                </UFormField>
                <UFormField
                    label="Slug"
                    required
                    :error="
                        form.slug !== '' && !slugValid
                            ? 'Lowercase letters, digits, hyphens only'
                            : undefined
                    "
                >
                    <UInput
                        v-model="form.slug"
                        placeholder="acme"
                        class="w-full"
                        @input="slugTouched = true"
                    />
                </UFormField>
                <UFormField label="Admin email" required>
                    <UInput
                        v-model="form.adminEmail"
                        type="email"
                        placeholder="admin@acme.com"
                        class="w-full"
                    />
                </UFormField>
                <UFormField label="Admin password" required help="Minimum 8 characters.">
                    <UInput
                        v-model="form.adminPassword"
                        type="password"
                        placeholder="••••••••"
                        class="w-full"
                    />
                </UFormField>
            </form>
        </AppConfirmModal>
    </div>
</template>
