<script setup lang="ts">
const colorMode = useColorMode()
const open = ref(false)

const {
    neutralColors,
    neutral,
    primaryColors,
    primary,
    blackAsPrimary,
    setBlackAsPrimary,
    radiuses,
    radius,
    fonts,
    font,
    icon,
    icons,
    modes,
    mode,
    hasCSSChanges,
    hasConfigChanges,
    resetTheme,
} = useTheme()
</script>

<template>
    <UPopover
        v-model:open="open"
        :ui="{
            content: 'w-72 px-6 py-4 flex flex-col gap-4 overflow-y-auto max-h-[calc(100vh-5rem)]',
        }"
    >
        <template #default>
            <UButton
                icon="i-lucide-droplet"
                color="neutral"
                :variant="open ? 'soft' : 'ghost'"
                square
                aria-label="Color picker"
                :ui="{ leadingIcon: 'text-primary' }"
            />
        </template>

        <template #content>
            <fieldset>
                <legend
                    class="mb-2 flex items-center gap-1 text-[11px] leading-none font-semibold select-none"
                >
                    Primary
                </legend>

                <div class="-mx-2 grid grid-cols-3 gap-1">
                    <ThemePickerButton
                        label="Black"
                        :selected="blackAsPrimary"
                        @click="setBlackAsPrimary(true)"
                    >
                        <template #leading>
                            <span class="inline-block size-2 rounded-full bg-black dark:bg-white" />
                        </template>
                    </ThemePickerButton>

                    <ThemePickerButton
                        v-for="color in primaryColors"
                        :key="color"
                        :label="color"
                        :chip="color"
                        :selected="!blackAsPrimary && primary === color"
                        @click="primary = color"
                    />
                </div>
            </fieldset>

            <fieldset>
                <legend
                    class="mb-2 flex items-center gap-1 text-[11px] leading-none font-semibold select-none"
                >
                    Neutral
                </legend>

                <div class="-mx-2 grid grid-cols-3 gap-1">
                    <ThemePickerButton
                        v-for="color in neutralColors"
                        :key="color"
                        :label="color"
                        :chip="color"
                        :selected="neutral === color"
                        @click="neutral = color"
                    />
                </div>
            </fieldset>

            <fieldset>
                <legend
                    class="mb-2 flex items-center gap-1 text-[11px] leading-none font-semibold select-none"
                >
                    Radius
                </legend>

                <div class="-mx-2 grid grid-cols-5 gap-1">
                    <ThemePickerButton
                        v-for="r in radiuses"
                        :key="r"
                        :label="String(r)"
                        class="justify-center px-0"
                        :selected="radius === r"
                        @click="radius = r"
                    />
                </div>
            </fieldset>

            <fieldset>
                <legend
                    class="mb-2 flex items-center gap-1 text-[11px] leading-none font-semibold select-none"
                >
                    Font
                </legend>

                <div class="-mx-2">
                    <USelect
                        v-model="font"
                        size="sm"
                        color="neutral"
                        icon="i-lucide-type"
                        :items="fonts"
                        class="ring-default hover:bg-elevated/50 data-[state=open]:bg-elevated/50 w-full rounded-sm text-[11px]"
                        :ui="{
                            trailingIcon:
                                'group-data-[state=open]:rotate-180 transition-transform duration-200',
                        }"
                    />
                </div>
            </fieldset>

            <fieldset>
                <legend
                    class="mb-2 flex items-center gap-1 text-[11px] leading-none font-semibold select-none"
                >
                    Icons
                </legend>

                <div class="-mx-2">
                    <USelect
                        v-model="icon"
                        size="sm"
                        color="neutral"
                        :icon="icons.find((i) => i.value === icon)?.icon"
                        :items="icons"
                        class="ring-default hover:bg-elevated/50 data-[state=open]:bg-elevated/50 w-full rounded-sm text-[11px] capitalize"
                        :ui="{
                            item: 'capitalize text-[11px]',
                            trailingIcon:
                                'group-data-[state=open]:rotate-180 transition-transform duration-200',
                        }"
                    />
                </div>
            </fieldset>

            <fieldset>
                <legend
                    class="mb-2 flex items-center gap-1 text-[11px] leading-none font-semibold select-none"
                >
                    Color Mode
                </legend>

                <div class="-mx-2 grid grid-cols-3 gap-1">
                    <ThemePickerButton
                        v-for="m in modes"
                        :key="m.label"
                        v-bind="m"
                        :selected="colorMode.preference === m.label"
                        @click="mode = m.label"
                    />
                </div>
            </fieldset>

            <fieldset v-if="hasCSSChanges || hasConfigChanges">
                <legend class="mb-2 text-[11px] leading-none font-semibold select-none">
                    Reset
                </legend>

                <div class="-mx-2 flex items-center justify-between gap-1">
                    <span class="text-destructive text-xs">
                        Reset your theme to default values.
                    </span>
                    <UTooltip text="Reset theme">
                        <UButton
                            color="neutral"
                            variant="outline"
                            size="sm"
                            icon="i-lucide-rotate-ccw"
                            class="ring-default hover:bg-elevated/50 ms-auto"
                            @click="resetTheme"
                        />
                    </UTooltip>
                </div>
            </fieldset>
        </template>
    </UPopover>
</template>
