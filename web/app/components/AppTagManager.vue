<script setup lang="ts">
import type { TagDto } from '~/types/leads'

/**
 * Tag sidebar card shared by detail pages (leads, persons). Renders the current
 * tags as removable chips plus a searchable add list. The add/remove controls
 * only show when `canEdit` is set (gate this on the entity's edit permission).
 *
 * Self-contained: POSTs `{ tagId }` to `endpoint` to add and DELETEs
 * `endpoint/{tagId}` to remove, emitting `changed` so the parent can refresh.
 *
 * Props are typed to the minimal `TagDto` shape (`id`/`name`/`color`) so both
 * embedded tags (`Product`/`Warehouse` carry `TagDto[]`) and the full tag
 * catalogue (`TagResponse[]`, a structural superset) flow in without casts.
 */
const props = defineProps<{
    /** Tags currently attached to the entity. */
    tags: TagDto[]
    /** All available tags to choose from. */
    allTags: TagDto[]
    /** Tag collection endpoint, e.g. `/api/leads/{id}/tags`. */
    endpoint: string
    /** Whether add/remove controls are shown. */
    canEdit?: boolean
}>()
const emit = defineEmits<{ changed: [] }>()

const api = useApi()
const toast = useToast()
const search = ref('')
const adding = ref(false)

const filteredTags = computed(() => {
    const existing = new Set(props.tags.map((t) => t.id))
    return props.allTags.filter(
        (t) => !existing.has(t.id) && t.name.toLowerCase().includes(search.value.toLowerCase())
    )
})

async function addTag(tag: TagDto) {
    adding.value = true
    try {
        await api(props.endpoint, { method: 'POST', body: { tagId: tag.id } })
        emit('changed')
    } catch {
        toast.add({ title: 'Failed to add tag', color: 'error' })
    } finally {
        adding.value = false
    }
}

async function removeTag(tagId: string) {
    try {
        await api(`${props.endpoint}/${tagId}`, { method: 'DELETE' })
        emit('changed')
    } catch {
        toast.add({ title: 'Failed to remove tag', color: 'error' })
    }
}
</script>

<template>
    <UCard>
        <template #header><p class="text-highlighted font-semibold">Tags</p></template>
        <div class="space-y-3">
            <div class="flex flex-wrap gap-1.5">
                <span
                    v-for="tag in tags"
                    :key="tag.id"
                    class="border-default flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium"
                    :style="{ borderColor: tag.color, color: tag.color }"
                >
                    {{ tag.name }}
                    <button v-if="canEdit" class="hover:opacity-70" @click="removeTag(tag.id)">
                        <UIcon name="i-tabler-x" class="size-3" />
                    </button>
                </span>
                <span v-if="!tags.length" class="text-muted text-xs">No tags</span>
            </div>
            <template v-if="canEdit">
                <UInput v-model="search" placeholder="Add tag…" size="sm" icon="i-tabler-search" />
                <div class="max-h-40 space-y-1 overflow-y-auto">
                    <button
                        v-for="tag in filteredTags"
                        :key="tag.id"
                        class="hover:bg-muted flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs transition-colors"
                        :disabled="adding"
                        @click="addTag(tag)"
                    >
                        <span
                            class="size-2 rounded-full"
                            :style="{ backgroundColor: tag.color ?? '#888' }"
                        />
                        {{ tag.name }}
                    </button>
                </div>
            </template>
        </div>
    </UCard>
</template>
