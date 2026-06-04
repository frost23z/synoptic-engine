<script setup lang="ts">
import type { DataGridFilterResponse } from '~/types/settings'

/**
 * Saved-view picker for a list page, backed by `useSavedFilters`. Lists the
 * current user's saved datagrid filters for `src`, applies one (emitting its
 * `applied` map), and — with `datagrid-filters.edit` — saves the current
 * filter state as a new view or deletes an existing one.
 *
 * Renders nothing when the user lacks `datagrid-filters.view`.
 */
const props = defineProps<{
    /** The list this picker belongs to, e.g. `leads`. */
    src: string
    /** Current filter state to persist when saving a new view. */
    applied: Record<string, unknown>
}>()

const emit = defineEmits<{ apply: [applied: Record<string, unknown>] }>()

const toast = useToast()
const { filters, canView, canEdit, refresh, save, remove } = useSavedFilters(props.src)

const open = ref(false)
const saveName = ref('')
const saving = ref(false)

onMounted(refresh)

function applyFilter(f: DataGridFilterResponse) {
    emit('apply', f.applied)
    open.value = false
}

async function submitSave() {
    const name = saveName.value.trim()
    if (!name) return
    saving.value = true
    try {
        await save(name, props.applied)
        saveName.value = ''
        toast.add({ title: 'View saved', color: 'success' })
    } catch (err: unknown) {
        const e = err as { data?: { message?: string } }
        toast.add({ title: 'Failed to save view', description: e?.data?.message, color: 'error' })
    } finally {
        saving.value = false
    }
}

async function removeFilter(f: DataGridFilterResponse) {
    try {
        await remove(f.id)
        toast.add({ title: 'View removed', color: 'success' })
    } catch {
        toast.add({ title: 'Failed to remove view', color: 'error' })
    }
}
</script>

<template>
    <UPopover v-if="canView" v-model:open="open">
        <UButton
            icon="i-tabler-filter-cog"
            :label="filters.length ? `Views (${filters.length})` : 'Views'"
            color="neutral"
            variant="outline"
        />
        <template #content>
            <div class="w-72 space-y-3 p-3">
                <p class="text-highlighted text-sm font-semibold">Saved views</p>
                <p v-if="!filters.length" class="text-muted text-sm">No saved views yet</p>
                <ul v-else class="space-y-1">
                    <li v-for="f in filters" :key="f.id" class="flex items-center gap-1">
                        <UButton
                            :label="f.name"
                            color="neutral"
                            variant="ghost"
                            size="sm"
                            block
                            class="flex-1 justify-start"
                            @click="applyFilter(f)"
                        />
                        <UButton
                            v-if="canEdit"
                            icon="i-tabler-trash"
                            color="error"
                            variant="ghost"
                            size="xs"
                            @click="removeFilter(f)"
                        />
                    </li>
                </ul>
                <template v-if="canEdit">
                    <USeparator />
                    <form class="flex gap-2" @submit.prevent="submitSave">
                        <UInput
                            v-model="saveName"
                            placeholder="Save current view…"
                            size="sm"
                            class="flex-1"
                        />
                        <UButton
                            icon="i-tabler-device-floppy"
                            size="sm"
                            :loading="saving"
                            :disabled="!saveName.trim()"
                            @click="submitSave"
                        />
                    </form>
                </template>
            </div>
        </template>
    </UPopover>
</template>
