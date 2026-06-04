<script setup lang="ts">
import type { SystemConfigGroupResponse } from '~/types/settings'

definePageMeta({ title: 'System Config' })
useHead({ title: 'System Config — Synoptic' })

const api = useApi()
const toast = useToast()
const { can } = usePermissions()

const canEdit = computed(() => can('settings.edit'))

const {
    data: groups,
    pending,
    refresh,
} = await useAsyncData<SystemConfigGroupResponse[]>('system-config', () =>
    api<SystemConfigGroupResponse[]>('/api/settings/config')
)

// Draft + original values keyed by config code. Secret items always start blank
// (the backend masks set secrets as `***`); a blank secret draft means "leave
// unchanged" so we never write the mask back.
const draft = ref<Record<string, string>>({})
const original = ref<Record<string, string>>({})

watch(
    groups,
    (gs) => {
        const next: Record<string, string> = {}
        for (const g of gs ?? []) {
            for (const item of g.items) {
                next[item.code] = item.isSecret ? '' : (item.value ?? '')
            }
        }
        draft.value = { ...next }
        original.value = next
    },
    { immediate: true }
)

const dirtyCodes = computed(() =>
    Object.keys(draft.value).filter((code) => draft.value[code] !== original.value[code])
)

const GROUP_ICONS: Record<string, string> = {
    general: 'i-tabler-settings',
    mail: 'i-tabler-mail',
    security: 'i-tabler-shield-lock',
    api: 'i-tabler-plug',
}
const groupIcon = (g: string) => GROUP_ICONS[g] ?? 'i-tabler-adjustments'

const saving = ref(false)

async function save() {
    if (!dirtyCodes.value.length) return
    saving.value = true
    try {
        for (const code of dirtyCodes.value) {
            const v = draft.value[code]
            await api(`/api/settings/config/${code}`, {
                method: 'PUT',
                body: { value: v === '' ? null : v },
            })
        }
        toast.add({ title: 'Settings saved', color: 'success' })
        await refresh()
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({
            title: 'Failed to save settings',
            description: e?.data?.message,
            color: 'error',
        })
    } finally {
        saving.value = false
    }
}

function reset() {
    draft.value = { ...original.value }
}
</script>

<template>
    <div class="space-y-4">
        <AppPageHeader title="System Config" subtitle="Per-tenant application settings">
            <template #actions>
                <UButton
                    v-if="canEdit"
                    label="Reset"
                    color="neutral"
                    variant="ghost"
                    :disabled="!dirtyCodes.length || saving"
                    @click="reset"
                />
                <UButton
                    v-if="canEdit"
                    icon="i-tabler-device-floppy"
                    :label="
                        dirtyCodes.length ? `Save ${dirtyCodes.length} change(s)` : 'Save changes'
                    "
                    :loading="saving"
                    :disabled="!dirtyCodes.length"
                    @click="save"
                />
            </template>
        </AppPageHeader>

        <div v-if="pending" class="space-y-4">
            <USkeleton v-for="i in 2" :key="i" class="h-48 w-full" />
        </div>

        <div v-else-if="!groups?.length">
            <AppEmptyState icon="i-tabler-settings-off" message="No configuration available" />
        </div>

        <UCard v-for="group in groups" v-else :key="group.group">
            <template #header>
                <div class="flex items-center gap-2">
                    <UIcon :name="groupIcon(group.group)" class="text-muted size-5" />
                    <p class="text-highlighted font-semibold capitalize">{{ group.group }}</p>
                </div>
            </template>

            <div class="space-y-4">
                <UFormField
                    v-for="item in group.items"
                    :key="item.code"
                    :label="item.label"
                    :help="item.code"
                >
                    <UTextarea
                        v-if="item.type === 'textarea'"
                        v-model="draft[item.code]"
                        :rows="2"
                        :disabled="!canEdit"
                        class="w-full"
                    />
                    <USwitch
                        v-else-if="item.type === 'boolean'"
                        :model-value="draft[item.code] === 'true'"
                        :disabled="!canEdit"
                        @update:model-value="draft[item.code] = $event ? 'true' : 'false'"
                    />
                    <UInput
                        v-else
                        v-model="draft[item.code]"
                        :type="
                            item.type === 'password'
                                ? 'password'
                                : item.type === 'number'
                                  ? 'number'
                                  : item.type === 'email'
                                    ? 'email'
                                    : 'text'
                        "
                        :placeholder="
                            item.isSecret && item.value === '***'
                                ? '•••••••• (set — leave blank to keep)'
                                : ''
                        "
                        :disabled="!canEdit"
                        class="w-full"
                    />
                </UFormField>
            </div>
        </UCard>
    </div>
</template>
