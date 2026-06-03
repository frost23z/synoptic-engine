import type { PageResponse } from '~/types/api'

interface PaginatedListOptions {
    /** Cache key prefix for `useAsyncData` (defaults to the endpoint). */
    key?: string
    /** Page size (defaults to {@link PAGE_SIZE}). */
    pageSize?: number
    /** Query-param name for the search term (backend default is `q`). */
    searchParam?: string
    /** Reactive extra query params (e.g. filters). Empty/undefined values are dropped. */
    params?: () => Record<string, string | number | undefined>
}

/**
 * Standard paginated-list fetcher used by every list page. Wraps the repeated
 * `useAsyncData` + page/search/queryKey boilerplate.
 *
 * Backend pages are 0-based; the returned `page` ref is 1-based (for `UPagination`).
 *
 * @example
 * const { items, total, page, search, pending, refresh } =
 *     await usePaginatedList<LeadResponse>('/api/leads', { params: () => ({ pipelineId }) })
 */
export async function usePaginatedList<T>(endpoint: string, options: PaginatedListOptions = {}) {
    const api = useApi()
    const pageSize = options.pageSize ?? PAGE_SIZE
    const page = ref(1)
    const search = ref('')
    const debouncedSearch = refDebounced(search, SEARCH_DEBOUNCE_MS)
    const baseKey = options.key ?? endpoint

    const queryKey = computed(() => [
        baseKey,
        page.value,
        debouncedSearch.value,
        JSON.stringify(options.params?.() ?? {}),
    ])

    const asyncData = await useAsyncData<PageResponse<T>>(
        () => queryKey.value.join('|'),
        () => {
            const params: Record<string, string | number> = {
                page: page.value - 1,
                size: pageSize,
            }
            if (debouncedSearch.value) params[options.searchParam ?? 'q'] = debouncedSearch.value
            for (const [k, v] of Object.entries(options.params?.() ?? {})) {
                if (v !== undefined && v !== '') params[k] = v
            }
            return api<PageResponse<T>>(endpoint, { params })
        },
        { watch: [queryKey] }
    )

    const items = computed(() => asyncData.data.value?.content ?? [])
    const total = computed(() => asyncData.data.value?.totalElements ?? 0)

    return {
        page,
        pageSize,
        search,
        items,
        total,
        pending: asyncData.pending,
        refresh: asyncData.refresh,
        data: asyncData.data,
    }
}
