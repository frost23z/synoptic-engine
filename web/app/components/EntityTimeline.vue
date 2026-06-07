<script setup lang="ts">
import type { ActivityResponse } from '~/types/activities'
import { ACTIVITY_TYPE_ICON } from '~/types/activities'

/**
 * Activity timeline shared by detail pages (leads, organizations). Renders a
 * divided list of activities with a type icon, title, comment and relative
 * date. When `canToggle` is set, each row shows a done toggle that emits
 * `toggleDone`.
 */
defineProps<{
    activities: ActivityResponse[]
    emptyMessage?: string
    /** Show the done toggle (gate on the activities edit permission). */
    canToggle?: boolean
}>()
const emit = defineEmits<{ toggleDone: [activity: ActivityResponse] }>()

const { formatRelativeDate } = useFormatters()
</script>

<template>
    <div v-if="!activities.length" class="text-muted py-6 text-center text-sm">
        {{ emptyMessage ?? 'No activities yet' }}
    </div>
    <ul v-else class="divide-default divide-y">
        <li
            v-for="act in activities"
            :key="act.id"
            class="flex items-start gap-3 py-3"
            :class="act.isDone ? 'opacity-60' : ''"
        >
            <div class="bg-muted mt-0.5 shrink-0 rounded-full p-1.5">
                <UIcon
                    :name="ACTIVITY_TYPE_ICON[act.type] ?? 'i-tabler-activity'"
                    class="text-muted size-3.5"
                />
            </div>
            <div class="min-w-0 flex-1">
                <p
                    class="text-default text-sm font-medium"
                    :class="act.isDone ? 'line-through' : ''"
                >
                    {{ act.title }}
                </p>
                <p v-if="act.comment" class="text-muted mt-0.5 text-xs">{{ act.comment }}</p>
                <p class="text-muted mt-0.5 text-xs">{{ formatRelativeDate(act.scheduleFrom) }}</p>
            </div>
            <UButton
                v-if="canToggle"
                :icon="act.isDone ? 'i-tabler-circle-check-filled' : 'i-tabler-circle'"
                :color="act.isDone ? 'success' : 'neutral'"
                variant="ghost"
                size="xs"
                @click="emit('toggleDone', act)"
            />
        </li>
    </ul>
</template>
