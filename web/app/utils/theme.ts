const iconMap = {
    // Nuxt UI required ↓
    arrowDown: {
        lucide: 'i-lucide-arrow-down',
        tabler: 'i-tabler-arrow-down',
        phosphor: 'i-ph-arrow-down',
    },
    arrowLeft: {
        lucide: 'i-lucide-arrow-left',
        tabler: 'i-tabler-arrow-left',
        phosphor: 'i-ph-arrow-left',
    },
    arrowRight: {
        lucide: 'i-lucide-arrow-right',
        tabler: 'i-tabler-arrow-right',
        phosphor: 'i-ph-arrow-right',
    },
    arrowUp: {
        lucide: 'i-lucide-arrow-up',
        tabler: 'i-tabler-arrow-up',
        phosphor: 'i-ph-arrow-up',
    },
    check: { lucide: 'i-lucide-check', tabler: 'i-tabler-check', phosphor: 'i-ph-check' },
    chevronDoubleLeft: {
        lucide: 'i-lucide-chevrons-left',
        tabler: 'i-tabler-chevrons-left',
        phosphor: 'i-ph-caret-double-left',
    },
    chevronDoubleRight: {
        lucide: 'i-lucide-chevrons-right',
        tabler: 'i-tabler-chevrons-right',
        phosphor: 'i-ph-caret-double-right',
    },
    chevronDown: {
        lucide: 'i-lucide-chevron-down',
        tabler: 'i-tabler-chevron-down',
        phosphor: 'i-ph-caret-down',
    },
    chevronLeft: {
        lucide: 'i-lucide-chevron-left',
        tabler: 'i-tabler-chevron-left',
        phosphor: 'i-ph-caret-left',
    },
    chevronRight: {
        lucide: 'i-lucide-chevron-right',
        tabler: 'i-tabler-chevron-right',
        phosphor: 'i-ph-caret-right',
    },
    chevronUp: {
        lucide: 'i-lucide-chevron-up',
        tabler: 'i-tabler-chevron-up',
        phosphor: 'i-ph-caret-up',
    },
    close: {
        lucide: 'i-lucide-x',
        tabler: 'i-tabler-x',
        phosphor: 'i-ph-x',
    },
    copy: { lucide: 'i-lucide-copy', tabler: 'i-tabler-copy', phosphor: 'i-ph-copy' },
    copyCheck: {
        lucide: 'i-lucide-copy-check',
        tabler: 'i-tabler-copy-check',
        phosphor: 'i-ph-check-circle',
    },
    dark: { lucide: 'i-lucide-moon', tabler: 'i-tabler-moon', phosphor: 'i-ph-moon' },
    drag: {
        lucide: 'i-lucide-grip-vertical',
        tabler: 'i-tabler-grip-vertical',
        phosphor: 'i-ph-dots-six-vertical',
    },
    ellipsis: { lucide: 'i-lucide-ellipsis', tabler: 'i-tabler-dots', phosphor: 'i-ph-dots-three' },
    error: {
        lucide: 'i-lucide-circle-x',
        tabler: 'i-tabler-square-rounded-x',
        phosphor: 'i-ph-x-circle',
    },
    external: {
        lucide: 'i-lucide-external-link',
        tabler: 'i-tabler-external-link',
        phosphor: 'i-ph-arrow-up-right',
    },
    eye: { lucide: 'i-lucide-eye', tabler: 'i-tabler-eye', phosphor: 'i-ph-eye' },
    eyeOff: { lucide: 'i-lucide-eye-off', tabler: 'i-tabler-eye-off', phosphor: 'i-ph-eye-slash' },
    file: { lucide: 'i-lucide-file', tabler: 'i-tabler-file', phosphor: 'i-ph-file' },
    folder: { lucide: 'i-lucide-folder', tabler: 'i-tabler-folder', phosphor: 'i-ph-folder' },
    folderOpen: {
        lucide: 'i-lucide-folder-open',
        tabler: 'i-tabler-folder-open',
        phosphor: 'i-ph-folder-open',
    },
    hash: { lucide: 'i-lucide-hash', tabler: 'i-tabler-hash', phosphor: 'i-ph-hash' },
    info: {
        lucide: 'i-lucide-info',
        tabler: 'i-tabler-info-square-rounded',
        phosphor: 'i-ph-info',
    },
    light: { lucide: 'i-lucide-sun', tabler: 'i-tabler-sun', phosphor: 'i-ph-sun' },
    loading: {
        lucide: 'i-lucide-loader',
        tabler: 'i-tabler-loader-2',
        phosphor: 'i-ph-circle-notch',
    },
    menu: { lucide: 'i-lucide-menu', tabler: 'i-tabler-menu', phosphor: 'i-ph-list' },
    minus: { lucide: 'i-lucide-minus', tabler: 'i-tabler-minus', phosphor: 'i-ph-minus' },
    panelClose: {
        lucide: 'i-lucide-panel-left-close',
        tabler: 'i-tabler-layout-sidebar-left-collapse',
        phosphor: 'i-ph-caret-left',
    },
    panelOpen: {
        lucide: 'i-lucide-panel-left-open',
        tabler: 'i-tabler-layout-sidebar-left-expand',
        phosphor: 'i-ph-caret-right',
    },
    plus: { lucide: 'i-lucide-plus', tabler: 'i-tabler-plus', phosphor: 'i-ph-plus' },
    search: {
        lucide: 'i-lucide-search',
        tabler: 'i-tabler-search',
        phosphor: 'i-ph-magnifying-glass',
    },
    success: {
        lucide: 'i-lucide-circle-check',
        tabler: 'i-tabler-square-rounded-check',
        phosphor: 'i-ph-check-circle',
    },
    system: {
        lucide: 'i-lucide-monitor',
        tabler: 'i-tabler-device-desktop',
        phosphor: 'i-ph-monitor',
    },
    upload: { lucide: 'i-lucide-upload', tabler: 'i-tabler-upload', phosphor: 'i-ph-upload' },
    warning: {
        lucide: 'i-lucide-triangle-alert',
        tabler: 'i-tabler-alert-triangle',
        phosphor: 'i-ph-warning',
    },
    caution: {
        lucide: 'i-lucide-alert-circle',
        tabler: 'i-tabler-alert-square-rounded',
        phosphor: 'i-ph-warning-diamond',
    },
    reload: {
        lucide: 'i-lucide-refresh-cw',
        tabler: 'i-tabler-refresh',
        phosphor: 'i-ph-arrows-clockwise',
    },
    stop: {
        lucide: 'i-lucide-square',
        tabler: 'i-tabler-square',
        phosphor: 'i-ph-square',
    },
    tip: {
        lucide: 'i-lucide-lightbulb',
        tabler: 'i-tabler-bulb',
        phosphor: 'i-ph-lightbulb',
    },

    // ─── Your custom icons ↓ ─────────────────────────────────────────────────
    dashboard: {
        lucide: 'i-lucide-layout-dashboard',
        tabler: 'i-tabler-layout-dashboard',
        phosphor: 'i-ph-squares-four',
    },
    user: { lucide: 'i-lucide-user', tabler: 'i-tabler-user', phosphor: 'i-ph-user' },
    users: { lucide: 'i-lucide-users', tabler: 'i-tabler-users', phosphor: 'i-ph-users' },
    settings: { lucide: 'i-lucide-settings', tabler: 'i-tabler-settings', phosphor: 'i-ph-gear' },
    logout: { lucide: 'i-lucide-log-out', tabler: 'i-tabler-logout', phosphor: 'i-ph-sign-out' },
    bell: { lucide: 'i-lucide-bell', tabler: 'i-tabler-bell', phosphor: 'i-ph-bell' },
    calendar: {
        lucide: 'i-lucide-calendar',
        tabler: 'i-tabler-calendar',
        phosphor: 'i-ph-calendar',
    },
    chart: {
        lucide: 'i-lucide-bar-chart-2',
        tabler: 'i-tabler-chart-bar',
        phosphor: 'i-ph-chart-bar',
    },
    home: { lucide: 'i-lucide-house', tabler: 'i-tabler-home', phosphor: 'i-ph-house' },
    inbox: { lucide: 'i-lucide-inbox', tabler: 'i-tabler-inbox', phosphor: 'i-ph-tray' },
} as const

// ─── Types ────────────────────────────────────────────────────────────────────
export type IconSetName = 'lucide' | 'tabler' | 'phosphor'
export type IconKey = keyof typeof iconMap

// ─── Build per-set maps (what appConfig.ui.icons expects) ─────────────────────
type IconMap = Record<IconKey, string>

function buildSets(map: typeof iconMap): Record<IconSetName, IconMap> {
    const sets = {} as Record<IconSetName, IconMap>
    const setNames: IconSetName[] = ['lucide', 'tabler', 'phosphor']
    for (const set of setNames) {
        sets[set] = Object.fromEntries(
            Object.entries(map).map(([key, val]) => [key, val[set]])
        ) as IconMap
    }
    return sets
}

export const themeIcons = buildSets(iconMap)

export const cssVariableDefaults = {
    light: {
        '--ui-text-dimmed': 'var(--ui-color-neutral-400)',
        '--ui-text-muted': 'var(--ui-color-neutral-500)',
        '--ui-text-toned': 'var(--ui-color-neutral-600)',
        '--ui-text': 'var(--ui-color-neutral-700)',
        '--ui-text-highlighted': 'var(--ui-color-neutral-900)',
        '--ui-text-inverted': 'white',
        '--ui-bg': 'white',
        '--ui-bg-muted': 'var(--ui-color-neutral-50)',
        '--ui-bg-elevated': 'var(--ui-color-neutral-100)',
        '--ui-bg-accented': 'var(--ui-color-neutral-200)',
        '--ui-bg-inverted': 'var(--ui-color-neutral-900)',
        '--ui-border': 'var(--ui-color-neutral-200)',
        '--ui-border-muted': 'var(--ui-color-neutral-200)',
        '--ui-border-accented': 'var(--ui-color-neutral-300)',
        '--ui-border-inverted': 'var(--ui-color-neutral-900)',
    },
    dark: {
        '--ui-text-dimmed': 'var(--ui-color-neutral-500)',
        '--ui-text-muted': 'var(--ui-color-neutral-400)',
        '--ui-text-toned': 'var(--ui-color-neutral-300)',
        '--ui-text': 'var(--ui-color-neutral-200)',
        '--ui-text-highlighted': 'white',
        '--ui-text-inverted': 'var(--ui-color-neutral-900)',
        '--ui-bg': 'var(--ui-color-neutral-900)',
        '--ui-bg-muted': 'var(--ui-color-neutral-800)',
        '--ui-bg-elevated': 'var(--ui-color-neutral-800)',
        '--ui-bg-accented': 'var(--ui-color-neutral-700)',
        '--ui-bg-inverted': 'white',
        '--ui-border': 'var(--ui-color-neutral-800)',
        '--ui-border-muted': 'var(--ui-color-neutral-700)',
        '--ui-border-accented': 'var(--ui-color-neutral-700)',
        '--ui-border-inverted': 'white',
    },
} as const
