<script setup lang="ts">
/** Centered pagination, rendered only when there is more than one page. */
const props = withDefaults(defineProps<{ page: number; total: number; pageSize?: number }>(), {
    pageSize: PAGE_SIZE,
})
defineEmits<{ 'update:page': [page: number] }>()
const { total, pageSize } = toRefs(props)
</script>

<template>
    <div v-if="total > pageSize" class="flex justify-center">
        <UPagination
            :page="page"
            :total="total"
            :items-per-page="pageSize"
            :sibling-count="1"
            show-edges
            @update:page="$emit('update:page', $event)"
        />
    </div>
</template>
