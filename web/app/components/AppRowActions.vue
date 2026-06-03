<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'

/**
 * Row-level "⋮" actions menu used in list tables. Build items with `onSelect`
 * handlers (or `to` for links) — note `@nuxt/ui` v4 uses `onSelect`, not `click`.
 */
defineProps<{ items: DropdownMenuItem[][] }>()
</script>

<template>
    <UDropdownMenu :items="items">
        <UButton icon="i-tabler-dots-vertical" color="neutral" variant="ghost" size="xs" />
    </UDropdownMenu>
</template>
