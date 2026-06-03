interface DeleteResourceOptions<T> {
    /** Builds the DELETE endpoint for the targeted item. */
    endpoint: (item: T) => string
    successMessage?: string
    errorMessage?: string
    /** Called after a successful delete (e.g. to refresh the list). */
    onDeleted?: () => void | Promise<void>
}

/**
 * Encapsulates the delete-confirmation flow duplicated across ~20 pages:
 * modal open state, the targeted row, the loading flag, and the confirm handler
 * (with success/error toasts). Pair with `<AppConfirmModal>`.
 *
 * @example
 * const del = useDeleteResource<LeadResponse>({
 *     endpoint: (l) => `/api/leads/${l.id}`,
 *     successMessage: 'Lead deleted',
 *     onDeleted: refresh,
 * })
 * // del.prompt(lead) to open, then <AppConfirmModal v-model:open="del.open" :loading="del.deleting" @confirm="del.confirm">
 */
export function useDeleteResource<T extends { id: string }>(options: DeleteResourceOptions<T>) {
    const api = useApi()
    const toast = useToast()

    const open = ref(false)
    const target = shallowRef<T | null>(null)
    const deleting = ref(false)

    function prompt(item: T) {
        target.value = item
        open.value = true
    }

    async function confirm() {
        if (!target.value) return
        deleting.value = true
        try {
            await api(options.endpoint(target.value), { method: 'DELETE' })
            toast.add({ title: options.successMessage ?? 'Deleted', color: 'success' })
            open.value = false
            await options.onDeleted?.()
        } catch {
            toast.add({ title: options.errorMessage ?? 'Failed to delete', color: 'error' })
        } finally {
            deleting.value = false
        }
    }

    return { open, target, deleting, prompt, confirm }
}
