import type { TenantResponse } from '~/types/sharing'

/**
 * Loads the tenant directory (`GET /api/tenants`) once and exposes helpers to
 * resolve a tenant UUID to a display name and to build `<USelect>` options.
 *
 * Requires `tenants.view`; without it the directory stays empty and
 * `tenantName` falls back to a shortened UUID (graceful degradation, since
 * relationship/share DTOs only carry tenant ids).
 *
 * Also exposes the caller's own tenant (`sessionTenantId` / `isSelf`), sourced
 * from `/api/auth/me`, so cross-tenant surfaces can render "us vs them".
 */
export function useTenantNames() {
    const api = useApi()
    const { can } = usePermissions()
    const authStore = useAuthStore()

    const sessionTenantId = computed(() => authStore.user?.tenantId ?? null)
    const isSelf = (id?: string | null): boolean => !!id && id === sessionTenantId.value

    const { data } = useAsyncData<TenantResponse[]>(
        'sharing-tenants',
        () => (can('tenants.view') ? api<TenantResponse[]>('/api/tenants') : Promise.resolve([])),
        { default: () => [] }
    )

    const tenants = computed(() => data.value ?? [])
    const byId = computed<Record<string, TenantResponse>>(() =>
        Object.fromEntries(tenants.value.map((t) => [t.id, t]))
    )

    function tenantName(id?: string | null): string {
        if (!id) return '—'
        return byId.value[id]?.name ?? `${id.slice(0, 8)}…`
    }

    const tenantOptions = computed(() => tenants.value.map((t) => ({ label: t.name, value: t.id })))
    const hasTenantList = computed(() => tenants.value.length > 0)

    return { tenants, tenantName, tenantOptions, hasTenantList, sessionTenantId, isSelf }
}
