<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

const authStore = useAuthStore()
const route = useRoute()
const { can } = usePermissions()

const open = useCookie<boolean>('sidebar-open', { default: () => true })

type NavEntry = NavigationMenuItem & { permission?: string }

const mainNav = computed<NavigationMenuItem[]>(() => [
    { label: 'Dashboard', icon: 'i-tabler-layout-dashboard', to: '/' },
])

const filterNav = (items: NavEntry[]): NavigationMenuItem[] =>
    items
        .filter((i) => !i.permission || can(i.permission))
        .map(({ permission: _p, ...rest }) => rest as NavigationMenuItem)

const crmNav = computed<NavigationMenuItem[]>(() =>
    filterNav([
        { label: 'CRM', type: 'label' },
        { label: 'Leads', icon: 'i-tabler-chart-bar', to: '/leads', permission: 'leads.view' },
        {
            label: 'Persons',
            icon: 'i-tabler-user',
            to: '/contacts/persons',
            permission: 'contacts.persons',
        },
        {
            label: 'Organizations',
            icon: 'i-tabler-building',
            to: '/contacts/organizations',
            permission: 'contacts.persons',
        },
        {
            label: 'Activities',
            icon: 'i-tabler-calendar-event',
            to: '/activities',
            permission: 'activities',
        },
        {
            label: 'Quotes',
            icon: 'i-tabler-file-invoice',
            to: '/quotes',
            permission: 'quotes',
        },
        { label: 'Mail', icon: 'i-tabler-mail', to: '/mail', permission: 'mail' },
    ])
)

const inventoryNav = computed<NavigationMenuItem[]>(() =>
    filterNav([
        { label: 'Inventory', type: 'label' },
        { label: 'Products', icon: 'i-tabler-box', to: '/products', permission: 'products.view' },
        {
            label: 'Warehouses',
            icon: 'i-tabler-building-warehouse',
            to: '/warehouses',
            permission: 'settings.inventory.warehouse',
        },
    ])
)

const settingsNav = computed<NavigationMenuItem[]>(() =>
    filterNav([
        { label: 'Settings', type: 'label' },
        {
            label: 'Users',
            icon: 'i-tabler-users',
            to: '/settings/users',
            permission: 'settings.user.users',
        },
        {
            label: 'Roles',
            icon: 'i-tabler-shield',
            to: '/settings/roles',
            permission: 'settings.user.roles',
        },
        {
            label: 'Groups',
            icon: 'i-tabler-users-group',
            to: '/settings/groups',
            permission: 'settings.user.groups',
        },
        {
            label: 'Pipelines',
            icon: 'i-tabler-git-merge',
            to: '/settings/pipelines',
            permission: 'settings',
        },
        {
            label: 'Lead Sources',
            icon: 'i-tabler-radar',
            to: '/settings/sources',
            permission: 'settings',
        },
        {
            label: 'Lead Types',
            icon: 'i-tabler-category',
            to: '/settings/types',
            permission: 'settings',
        },
        { label: 'Tags', icon: 'i-tabler-tag', to: '/settings/tags', permission: 'settings' },
        {
            label: 'Attributes',
            icon: 'i-tabler-list',
            to: '/settings/attributes',
            permission: 'settings',
        },
        {
            label: 'Web Forms',
            icon: 'i-tabler-forms',
            to: '/settings/web-forms',
            permission: 'settings',
        },
        {
            label: 'Workflows',
            icon: 'i-tabler-git-branch',
            to: '/settings/workflows',
            permission: 'settings.automation',
        },
        {
            label: 'Webhooks',
            icon: 'i-tabler-webhook',
            to: '/settings/webhooks',
            permission: 'settings.automation',
        },
        {
            label: 'Email Templates',
            icon: 'i-tabler-mail-cog',
            to: '/settings/email-templates',
            permission: 'settings',
        },
        {
            label: 'Marketing',
            icon: 'i-tabler-speakerphone',
            to: '/settings/marketing',
            permission: 'settings.automation',
        },
        {
            label: 'Imports',
            icon: 'i-tabler-upload',
            to: '/settings/imports',
            permission: 'settings.automation.data_transfer',
        },
    ])
)

const tooltipProps = { content: { side: 'right' as const }, delayDuration: 0 }

const user = computed(() => ({
    name: authStore.user?.fullName ?? 'User',
    avatar: {
        src: authStore.user?.avatar ?? 'https://github.com/benjamincanac.png',
        alt: authStore.user?.fullName ?? 'User',
    },
}))

const userMenuItems = computed(() => [
    [
        {
            label: authStore.user?.fullName ?? 'Account',
            type: 'label' as const,
        },
    ],
    [
        {
            label: 'Sign out',
            icon: 'i-tabler-logout',
            click: () => authStore.logout(),
        },
    ],
])
</script>

<template>
    <div class="flex flex-1">
        <USidebar v-model:open="open" collapsible="icon" rail>
            <!-- Logo -->
            <template #header="{ state }">
                <NuxtLink to="/" class="flex items-center gap-3">
                    <div
                        class="bg-primary flex size-8 shrink-0 items-center justify-center rounded-lg"
                    >
                        <UIcon name="i-lucide-rocket" class="size-5" text-neutral />
                    </div>
                    <span
                        v-if="state === 'expanded'"
                        class="text-highlighted text-lg font-semibold tracking-tight"
                    >
                        Synoptic
                    </span>
                </NuxtLink>
            </template>
            <!-- Navigation -->
            <template #default="{ state }">
                <div class="flex flex-col gap-2">
                    <UNavigationMenu
                        :items="mainNav"
                        orientation="vertical"
                        :collapsed="state === 'collapsed'"
                        :tooltip="tooltipProps"
                    />
                    <UNavigationMenu
                        :items="crmNav"
                        orientation="vertical"
                        :collapsed="state === 'collapsed'"
                        :tooltip="tooltipProps"
                    />
                    <UNavigationMenu
                        :items="inventoryNav"
                        orientation="vertical"
                        :collapsed="state === 'collapsed'"
                        :tooltip="tooltipProps"
                    />
                    <UNavigationMenu
                        :items="settingsNav"
                        orientation="vertical"
                        :collapsed="state === 'collapsed'"
                        :tooltip="tooltipProps"
                    />
                </div>
            </template>
            <!-- User profile -->
            <template #footer>
                <UDropdownMenu
                    :items="userMenuItems"
                    :content="{ align: 'center', collisionPadding: 12 }"
                    :ui="{ content: 'w-(--reka-dropdown-menu-trigger-width) min-w-48' }"
                >
                    <UButton
                        v-bind="user"
                        :label="user?.name"
                        trailing-icon="i-lucide-chevrons-up-down"
                        color="neutral"
                        variant="ghost"
                        square
                        class="data-[state=open]:bg-elevated w-full overflow-hidden"
                        :ui="{
                            trailingIcon: 'text-dimmed ms-auto',
                        }"
                    />
                </UDropdownMenu>
            </template>
        </USidebar>
        <div class="flex flex-1 flex-col">
            <header
                class="bg-default/75 border-default sticky top-0 z-50 flex h-(--ui-header-height) items-center gap-3 border-b px-4 backdrop-blur"
            >
                <UTooltip :text="open ? 'Collapse Sidebar' : 'Expand Sidebar'">
                    <UButton
                        :icon="open ? 'lucide:panel-left-close' : 'lucide:panel-left-open'"
                        color="neutral"
                        variant="ghost"
                        aria-label="Toggle sidebar"
                        @click="open = !open"
                    />
                </UTooltip>
                <h1 class="text-highlighted text-sm font-semibold">
                    {{ route.meta.title }}
                </h1>
                <div class="flex-1" />
                <ThemePicker />
            </header>
            <!-- Main area -->
            <UMain class="p-4">
                <slot />
            </UMain>
        </div>
    </div>
</template>
