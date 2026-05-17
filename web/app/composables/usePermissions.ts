/**
 * Provides permission helpers based on the authorities array
 * returned by the backend (e.g. "leads.view", "users.create").
 *
 * Supports dot-notation hierarchical keys with parent-key inheritance:
 * - Exact match: "leads.view"
 * - Parent match: "leads" grants "leads.view", "leads.create", etc.
 *
 * Usage:
 *   const { can, canAny, canAll } = usePermissions()
 *   if (can('leads.create')) { ... }
 *   if (canAny('users.edit', 'users.delete')) { ... }
 *   if (canAll('leads.view', 'leads.create')) { ... }
 */
export const usePermissions = () => {
    const authStore = useAuthStore()

    const can = (permission: string): boolean => {
        const authorities = authStore.user?.authorities ?? []
        if (authorities.includes(permission)) return true
        // walk up: "leads.create" → check "leads"
        const parts = permission.split('.')
        for (let i = parts.length - 1; i > 0; i--) {
            if (authorities.includes(parts.slice(0, i).join('.'))) return true
        }
        return false
    }

    const canAny = (...permissions: string[]): boolean => permissions.some((p) => can(p))

    const canAll = (...permissions: string[]): boolean => permissions.every((p) => can(p))

    return { can, canAny, canAll }
}
