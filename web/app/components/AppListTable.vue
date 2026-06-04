<script setup lang="ts" generic="T extends { id: string }">
import type { TableColumn } from '@nuxt/ui'

/**
 * List table used across all index pages. Wraps `UCard` + `UTable`, optionally
 * manages a leading selection checkbox column, and forwards every cell/header/
 * empty slot through to `UTable`.
 *
 * When `selectable`, pass `v-model:selected` (an array of row ids) and DO NOT
 * include a `select` column in `columns` — it is injected automatically.
 *
 * Below `md` the table collapses to one stacked card per row (label/value
 * pairs) so it stays usable on touch; the same `*-cell` slots are reused with a
 * synthetic `{ row: { original } }` payload (pages read `row.original.*`).
 */
const props = defineProps<{
    rows: T[]
    columns: TableColumn<T>[]
    loading?: boolean
    selectable?: boolean
    selected?: string[]
}>()
const emit = defineEmits<{ 'update:selected': [ids: string[]] }>()

const selectedIds = computed(() => props.selected ?? [])
const allSelected = computed(
    () => props.rows.length > 0 && selectedIds.value.length === props.rows.length
)
const indeterminate = computed(
    () => selectedIds.value.length > 0 && selectedIds.value.length < props.rows.length
)

function toggleAll() {
    emit('update:selected', allSelected.value ? [] : props.rows.map((r) => r.id))
}
function toggleRow(id: string) {
    emit(
        'update:selected',
        selectedIds.value.includes(id)
            ? selectedIds.value.filter((s) => s !== id)
            : [...selectedIds.value, id]
    )
}

const selectColumn: TableColumn<T> = {
    id: 'select',
    header: '',
    meta: { class: { th: 'w-8', td: 'w-8' } },
}
const allColumns = computed<TableColumn<T>[]>(() =>
    props.selectable ? [selectColumn, ...props.columns] : props.columns
)

// ── Mobile (stacked card) helpers ──────────────────────────────────────────
function colKey(c: TableColumn<T>): string {
    const cc = c as { accessorKey?: string; id?: string }
    return cc.accessorKey ?? cc.id ?? ''
}
const hasActions = computed(() => props.columns.some((c) => colKey(c) === 'actions'))
/** Columns rendered as label/value rows on mobile (actions handled separately). */
const mobileColumns = computed(() =>
    props.columns
        .filter((c) => colKey(c) !== 'actions')
        .map((c) => ({
            key: colKey(c),
            label: typeof c.header === 'string' ? c.header : colKey(c),
        }))
)
function rawValue(item: T, key: string): unknown {
    return (item as Record<string, unknown>)[key]
}
</script>

<template>
    <UCard :ui="{ body: 'p-0' }">
        <!-- md+ : real table -->
        <div class="hidden md:block">
            <UTable :data="rows" :columns="allColumns" :loading="loading" sticky>
                <template #select-header>
                    <UCheckbox
                        :checked="allSelected"
                        :indeterminate="indeterminate"
                        @change="toggleAll"
                    />
                </template>
                <template #select-cell="{ row }">
                    <UCheckbox
                        :checked="selectedIds.includes(row.original.id)"
                        @change="toggleRow(row.original.id)"
                    />
                </template>

                <!-- Forward all cell / header / empty slots the parent provides -->
                <template v-for="(_, name) in $slots" :key="name" #[name]="slotProps">
                    <slot :name="name" v-bind="slotProps ?? {}" />
                </template>
            </UTable>
        </div>

        <!-- < md : stacked cards -->
        <div class="divide-default divide-y md:hidden">
            <div v-if="loading" class="space-y-3 p-4">
                <USkeleton v-for="i in 5" :key="i" class="h-20 w-full rounded-lg" />
            </div>
            <div v-else-if="!rows.length" class="px-4">
                <slot name="empty">
                    <p class="text-muted py-8 text-center text-sm">No records found</p>
                </slot>
            </div>
            <div v-for="item in rows" v-else :key="item.id" class="space-y-2 p-4">
                <div
                    v-if="selectable || hasActions"
                    class="flex items-center justify-between gap-2"
                >
                    <UCheckbox
                        v-if="selectable"
                        :checked="selectedIds.includes(item.id)"
                        @change="toggleRow(item.id)"
                    />
                    <div v-if="hasActions" class="ml-auto">
                        <slot name="actions-cell" :row="{ original: item }" />
                    </div>
                </div>
                <dl class="space-y-1.5">
                    <div
                        v-for="col in mobileColumns"
                        :key="col.key"
                        class="flex items-baseline justify-between gap-3 text-sm"
                    >
                        <dt class="text-muted shrink-0 text-xs font-medium">{{ col.label }}</dt>
                        <dd class="min-w-0 truncate text-right">
                            <slot :name="`${col.key}-cell`" :row="{ original: item }">
                                {{ rawValue(item, col.key) ?? '—' }}
                            </slot>
                        </dd>
                    </div>
                </dl>
            </div>
        </div>
    </UCard>
</template>
