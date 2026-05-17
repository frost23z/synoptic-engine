export const useMassSelect = () => {
    const selected = ref<string[]>([])

    const isSelected = (id: string) => selected.value.includes(id)

    const toggle = (id: string) => {
        if (isSelected(id)) {
            selected.value = selected.value.filter((s) => s !== id)
        } else {
            selected.value = [...selected.value, id]
        }
    }

    const selectAll = (ids: string[]) => {
        selected.value = ids
    }

    const clearAll = () => {
        selected.value = []
    }

    const hasSelection = computed(() => selected.value.length > 0)
    const count = computed(() => selected.value.length)

    return { selected, isSelected, toggle, selectAll, clearAll, hasSelection, count }
}
