<script setup lang="ts">
/**
 * Standard detail/create page shell: a back button (router-link via `to`),
 * an optional title + subtitle on the left, an `#actions` slot on the right,
 * and the page content in the default slot.
 *
 * Use the `#subtitle` slot for rich subtitles (status badges, meta) — it
 * overrides the plain `subtitle` text. Override `rootClass` to constrain the
 * width / spacing (e.g. create pages: "mx-auto max-w-xl space-y-6").
 */
withDefaults(
    defineProps<{
        /** Back-button destination (router-link `to`). */
        to: string
        title?: string
        subtitle?: string
        /** Root container classes; override to constrain width/spacing. */
        rootClass?: string
    }>(),
    { rootClass: 'space-y-6' }
)
</script>

<template>
    <div :class="rootClass">
        <div class="flex items-start justify-between gap-4">
            <div class="flex items-center gap-3">
                <UButton icon="i-tabler-arrow-left" color="neutral" variant="ghost" :to="to" />
                <div v-if="title || $slots.subtitle">
                    <h2 v-if="title" class="text-highlighted text-xl font-semibold">
                        {{ title }}
                    </h2>
                    <slot name="subtitle">
                        <p v-if="subtitle" class="text-muted text-sm">{{ subtitle }}</p>
                    </slot>
                </div>
            </div>
            <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2">
                <slot name="actions" />
            </div>
        </div>
        <slot />
    </div>
</template>
