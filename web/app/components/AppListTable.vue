<script setup lang="ts" generic="T extends { id: string }">
import type { TableColumn } from '@nuxt/ui'

/**
 * List table used across all index pages. Wraps `UCard` + `UTable`, optionally
 * manages a leading selection checkbox column, and forwards every cell/header/
 * empty slot through to `UTable`.
 *
 * When `selectable`, pass `v-model:selected` (an array of row ids) and DO NOT
 * include a `select` column in `columns` — it is injected automatically.
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
</script>

<template>
    <UCard :ui="{ body: 'p-0' }">
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
    </UCard>
</template>
