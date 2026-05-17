<script setup lang="ts">
defineProps<{
    label: string
    icon?: string
    chip?: string
    selected?: boolean
}>()

const slots = defineSlots<{
    leading: () => unknown
}>()

const colorMode = useColorMode()
</script>

<template>
    <UButton
        size="sm"
        color="neutral"
        variant="outline"
        :icon="icon"
        :label="label"
        class="ring-default rounded-sm text-[11px] capitalize"
        :class="[selected ? 'bg-elevated' : 'hover:bg-elevated/50']"
    >
        <template v-if="chip || !!slots.leading" #leading>
            <slot name="leading">
                <!-- <UChip standalone inset /> -->
                <span
                    class="inline-block size-2 rounded-full"
                    :class="colorMode.value === 'dark' ? `bg-${chip}-400` : `bg-${chip}-500`"
                />
            </slot>
        </template>
    </UButton>
</template>
