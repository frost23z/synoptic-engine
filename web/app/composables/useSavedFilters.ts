import type { DataGridFilterResponse } from '~/types/settings'

/**
 * Loads + mutates a user's saved datagrid filters for a given list (`src`),
 * backed by `DataGridFilterController` (`/api/datagrid/saved-filters`).
 *
 * Reads require `datagrid-filters.view`; save/delete require `datagrid-filters.edit`.
 * Pair with `<AppSavedFilters>`.
 */
export function useSavedFilters(src: string) {
    const api = useApi()
    const { can } = usePermissions()

    const canView = can('datagrid-filters.view')
    const canEdit = can('datagrid-filters.edit')

    const filters = ref<DataGridFilterResponse[]>([])
    const pending = ref(false)

    async function refresh() {
        if (!canView) return
        pending.value = true
        try {
            filters.value = await api<DataGridFilterResponse[]>('/api/datagrid/saved-filters', {
                params: { src },
            })
        } finally {
            pending.value = false
        }
    }

    async function save(name: string, applied: Record<string, unknown>) {
        const created = await api<DataGridFilterResponse>('/api/datagrid/saved-filters', {
            method: 'POST',
            body: { name, src, applied },
        })
        filters.value = [...filters.value, created]
        return created
    }

    async function remove(id: string) {
        await api(`/api/datagrid/saved-filters/${id}`, { method: 'DELETE' })
        filters.value = filters.value.filter((f) => f.id !== id)
    }

    return { filters, pending, canView, canEdit, refresh, save, remove }
}
