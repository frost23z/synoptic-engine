<script setup lang="ts">
import type { ButtonProps } from '@nuxt/ui'

/**
 * Confirmation modal (delete, status change, etc). Body goes in the default slot.
 * Use `v-model:open` and listen to `@confirm`.
 */
withDefaults(
    defineProps<{
        open: boolean
        title: string
        loading?: boolean
        confirmLabel?: string
        confirmColor?: ButtonProps['color']
        confirmDisabled?: boolean
        widthClass?: string
    }>(),
    { confirmLabel: 'Confirm', confirmColor: 'primary' }
)
defineEmits<{ 'update:open': [value: boolean]; confirm: [] }>()
</script>

<template>
    <UModal
        :open="open"
        :ui="widthClass ? { content: widthClass } : undefined"
        @update:open="$emit('update:open', $event)"
    >
        <template #content>
            <UCard>
                <template #header>
                    <p class="text-highlighted font-semibold">{{ title }}</p>
                </template>
                <slot />
                <template #footer>
                    <div class="flex justify-end gap-2">
                        <UButton
                            color="neutral"
                            variant="outline"
                            label="Cancel"
                            @click="$emit('update:open', false)"
                        />
                        <UButton
                            :color="confirmColor"
                            :label="confirmLabel"
                            :loading="loading"
                            :disabled="confirmDisabled"
                            @click="$emit('confirm')"
                        />
                    </div>
                </template>
            </UCard>
        </template>
    </UModal>
</template>
