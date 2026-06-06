import type { Ref } from 'vue'
import type { PipelineResponse, UserResponse } from '~/types/settings'

/** Minimal `{ id, name }` reference rows (lead sources / types). */
export interface NamedLookup {
    id: string
    name: string
}

interface SelectOption {
    label: string
    value: string
}

type LookupKey = 'pipelines' | 'sources' | 'types' | 'users'

/**
 * App-wide cache for the "hot" reference data shared across many create/edit
 * screens — pipelines, lead sources, lead types and users. Storage is backed by
 * `useState`, so every page shares **one** copy; each list is fetched at most
 * once per session (the loaders are idempotent). Call {@link invalidate} after a
 * mutation (e.g. creating a new source) to force the next access to refetch.
 *
 * Concurrent first-loads are de-duped via a `nuxtApp`-scoped in-flight map
 * (request-scoped on the server, a singleton on the client), so two selects on
 * the same page won't double-fetch.
 *
 * `users` is gated by `users.view` on the backend — only call `loadUsers()`
 * behind a `usePermissions().can('users.view')` check.
 */
export function useDomainLookups() {
    const api = useApi()
    const app = useNuxtApp() as unknown as {
        _domainLookupsInflight?: Partial<Record<LookupKey, Promise<void>>>
    }
    const inflight = (app._domainLookupsInflight ??= {})

    const pipelines = useState<PipelineResponse[]>('lookups:pipelines', () => [])
    const sources = useState<NamedLookup[]>('lookups:sources', () => [])
    const types = useState<NamedLookup[]>('lookups:types', () => [])
    const users = useState<UserResponse[]>('lookups:users', () => [])
    const loaded = useState<Record<LookupKey, boolean>>('lookups:loaded', () => ({
        pipelines: false,
        sources: false,
        types: false,
        users: false,
    }))

    function load<T>(key: LookupKey, target: Ref<T>, fetcher: () => Promise<T>): Promise<void> {
        if (loaded.value[key]) return Promise.resolve()
        return (inflight[key] ??= fetcher()
            .then((data) => {
                target.value = data
                loaded.value = { ...loaded.value, [key]: true }
            })
            .finally(() => {
                inflight[key] = undefined
            }))
    }

    const loadPipelines = () =>
        load('pipelines', pipelines, () => api<PipelineResponse[]>('/api/pipelines'))
    const loadSources = () =>
        load('sources', sources, () => api<NamedLookup[]>('/api/lead-sources'))
    const loadTypes = () => load('types', types, () => api<NamedLookup[]>('/api/lead-types'))
    const loadUsers = () => load('users', users, () => api<UserResponse[]>('/api/users'))

    /** Load the lead-form lookups (pipelines + sources + types) in parallel. */
    const loadLeadLookups = () => Promise.all([loadPipelines(), loadSources(), loadTypes()])

    /** Drop the cached copy of one or more lists so the next access refetches. */
    function invalidate(...keys: LookupKey[]) {
        const next = { ...loaded.value }
        for (const key of keys.length ? keys : (Object.keys(next) as LookupKey[])) {
            next[key] = false
            inflight[key] = undefined
        }
        loaded.value = next
    }

    const toOptions = (rows: NamedLookup[]): SelectOption[] =>
        rows.map((r) => ({ label: r.name, value: r.id }))

    const pipelineOptions = computed<SelectOption[]>(() =>
        pipelines.value.map((p) => ({ label: p.name, value: p.id }))
    )
    const sourceOptions = computed<SelectOption[]>(() => toOptions(sources.value))
    const typeOptions = computed<SelectOption[]>(() => toOptions(types.value))
    const userOptions = computed<SelectOption[]>(() =>
        users.value.map((u) => ({ label: u.fullName, value: u.id }))
    )

    /** The tenant's default pipeline (or the first one) — for pre-selecting forms. */
    const defaultPipeline = computed(
        () => pipelines.value.find((p) => p.isDefault) ?? pipelines.value[0]
    )

    const userNameById = computed(() =>
        Object.fromEntries(users.value.map((u) => [u.id, u.fullName]))
    )
    const userName = (id?: string | null): string => (id ? (userNameById.value[id] ?? '—') : '—')

    return {
        pipelines,
        sources,
        types,
        users,
        loadPipelines,
        loadSources,
        loadTypes,
        loadUsers,
        loadLeadLookups,
        invalidate,
        pipelineOptions,
        sourceOptions,
        typeOptions,
        userOptions,
        defaultPipeline,
        userName,
    }
}
