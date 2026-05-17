import { omit } from '#ui/utils'
import colors from 'tailwindcss/colors'
import { themeIcons } from '../utils/theme'

const DEFAULT_FONT = 'Public Sans'
const DEFAULT_RADIUS = 0.25
const DEFAULT_ICON_SET = 'lucide'

export function useTheme() {
    const appConfig = useAppConfig()
    const colorMode = useColorMode()

    const color = computed(() =>
        colorMode.value === 'dark'
            ? (colors as unknown as Record<string, Record<string, string>>)[
                  appConfig.ui.colors.neutral
              ]?.[900]
            : 'white'
    )

    const _radius = useLocalStorage('nuxt-ui-radius', DEFAULT_RADIUS)
    const _font = useLocalStorage('nuxt-ui-font', DEFAULT_FONT)
    const _iconSet = useLocalStorage('nuxt-ui-icons', DEFAULT_ICON_SET)
    const _blackAsPrimary = useLocalStorage('nuxt-ui-black-as-primary', false)

    const neutralColors = [
        'slate',
        'gray',
        'zinc',
        'neutral',
        'stone',
        'taupe',
        'mauve',
        'mist',
        'olive',
    ]
    const neutral = computed({
        get: () => appConfig.ui.colors.neutral,
        set: (option: string) => {
            appConfig.ui.colors.neutral = option
            window.localStorage.setItem('nuxt-ui-neutral', option)
        },
    })

    const colorsToOmit = ['inherit', 'current', 'transparent', 'black', 'white', ...neutralColors]
    const primaryColors = Object.keys(omit(colors, colorsToOmit as (keyof typeof colors)[]))
    const primary = computed({
        get: () => appConfig.ui.colors.primary,
        set: (option: string) => {
            appConfig.ui.colors.primary = option
            window.localStorage.setItem('nuxt-ui-primary', option)
            setBlackAsPrimary(false)
        },
    })

    const radiuses = [0, 0.125, 0.25, 0.375, 0.5]
    const radius = computed({
        get: () => _radius.value,
        set: (option: number) => {
            _radius.value = option
        },
    })

    const fonts = ['Public Sans', 'DM Sans', 'Geist', 'Inter', 'Poppins', 'Outfit', 'Raleway']
    const font = computed({
        get: () => _font.value,
        set: (option: string) => {
            _font.value = option
        },
    })

    const icons = [
        { label: 'Lucide', icon: 'i-lucide-feather', value: 'lucide' },
        { label: 'Phosphor', icon: 'i-ph-phosphor-logo', value: 'phosphor' },
        { label: 'Tabler', icon: 'i-tabler-brand-tabler', value: 'tabler' },
    ]
    const icon = computed({
        get: () => _iconSet.value,
        set: (option: string) => {
            _iconSet.value = option
            appConfig.ui.icons = themeIcons[
                option as keyof typeof themeIcons
            ] as unknown as typeof appConfig.ui.icons
            window.localStorage.setItem('nuxt-ui-icons', option)
        },
    })

    const iconsRecord = computed(() => appConfig.ui.icons as unknown as Record<string, string>)
    const modes = computed(() => [
        { label: 'light', icon: iconsRecord.value['light'] },
        { label: 'dark', icon: iconsRecord.value['dark'] },
        { label: 'system', icon: iconsRecord.value['system'] },
    ])
    const mode = computed({
        get: () => colorMode.value,
        set: (option: string) => {
            colorMode.preference = option
        },
    })

    const blackAsPrimary = computed(() => _blackAsPrimary.value)

    function setBlackAsPrimary(value: boolean) {
        _blackAsPrimary.value = value
    }

    const radiusStyle = computed(() => `:root { --ui-radius: ${_radius.value}rem; }`)
    const blackAsPrimaryStyle = computed(() =>
        _blackAsPrimary.value
            ? ':root { --ui-primary: black; } .dark { --ui-primary: white; }'
            : ':root {}'
    )
    const fontStyle = computed(() => `:root { --font-sans: '${_font.value}', sans-serif; }`)

    const link = computed(() => {
        const name = _font.value
        if (name === DEFAULT_FONT) return []
        return [
            {
                rel: 'stylesheet' as const,
                href: `https://fonts.googleapis.com/css2?family=${encodeURIComponent(name)}:wght@400;500;600;700&display=swap`,
                id: `font-${name.toLowerCase().replace(/\s+/g, '-')}`,
            },
        ]
    })

    const style = [
        { innerHTML: radiusStyle, id: 'nuxt-ui-radius', tagPriority: -2 },
        { innerHTML: blackAsPrimaryStyle, id: 'nuxt-ui-black-as-primary', tagPriority: -2 },
        { innerHTML: fontStyle, id: 'nuxt-ui-font', tagPriority: -2 },
    ]

    const hasCSSChanges = computed(
        () =>
            _radius.value !== DEFAULT_RADIUS ||
            _blackAsPrimary.value ||
            _font.value !== DEFAULT_FONT
    )

    const hasConfigChanges = computed(
        () =>
            appConfig.ui.colors.primary !== 'green' ||
            appConfig.ui.colors.neutral !== 'slate' ||
            _iconSet.value !== DEFAULT_ICON_SET
    )

    function resetTheme() {
        appConfig.ui.colors.primary = 'green'
        window.localStorage.removeItem('nuxt-ui-primary')

        appConfig.ui.colors.neutral = 'slate'
        window.localStorage.removeItem('nuxt-ui-neutral')

        _radius.value = DEFAULT_RADIUS
        _font.value = DEFAULT_FONT
        _iconSet.value = DEFAULT_ICON_SET
        appConfig.ui.icons = themeIcons[DEFAULT_ICON_SET] as unknown as typeof appConfig.ui.icons
        _blackAsPrimary.value = false
    }

    return {
        color,
        style,
        link,
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
    }
}
