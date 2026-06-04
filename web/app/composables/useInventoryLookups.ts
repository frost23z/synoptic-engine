import type { PageResponse } from '~/types/api'
import type {
    ProductResponse,
    WarehouseLocationResponse,
    WarehouseResponse,
} from '~/types/inventory'

/** Page size used when pulling whole lookup lists for selects/name resolution. */
const LOOKUP_PAGE_SIZE = 500

interface SelectOption {
    label: string
    value: string
}

/**
 * Shared inventory lookups for the stock and transfer screens: products,
 * warehouses and warehouse locations, with id→label resolvers and `USelect`
 * option lists. Each loader is idempotent (loads at most once per instance) so
 * callers can call them freely from `onMounted`/watchers.
 *
 * Locations are fetched per-warehouse (`/api/warehouses/{id}/locations`) and
 * cached in `locationsByWarehouse`; `loadAllLocations()` flattens them for the
 * transfer list where rows reference arbitrary locations.
 */
export function useInventoryLookups() {
    const api = useApi()

    const products = shallowRef<ProductResponse[]>([])
    const warehouses = shallowRef<WarehouseResponse[]>([])
    const locations = shallowRef<WarehouseLocationResponse[]>([])
    const locationsByWarehouse = ref<Record<string, WarehouseLocationResponse[]>>({})

    let productsLoaded = false
    let warehousesLoaded = false
    let allLocationsLoaded = false

    async function loadProducts() {
        if (productsLoaded) return
        const page = await api<PageResponse<ProductResponse>>('/api/products', {
            params: { page: 0, size: LOOKUP_PAGE_SIZE },
        })
        products.value = page.content
        productsLoaded = true
    }

    async function loadWarehouses() {
        if (warehousesLoaded) return
        const page = await api<PageResponse<WarehouseResponse>>('/api/warehouses', {
            params: { page: 0, size: LOOKUP_PAGE_SIZE },
        })
        warehouses.value = page.content
        warehousesLoaded = true
    }

    /** Fetch (and cache) the locations of a single warehouse. */
    async function ensureLocations(warehouseId: string): Promise<WarehouseLocationResponse[]> {
        const cached = locationsByWarehouse.value[warehouseId]
        if (cached) return cached
        const locs = await api<WarehouseLocationResponse[]>(
            `/api/warehouses/${warehouseId}/locations`
        )
        locationsByWarehouse.value = { ...locationsByWarehouse.value, [warehouseId]: locs }
        return locs
    }

    /** Load every warehouse's locations and flatten them into `locations`. */
    async function loadAllLocations() {
        if (allLocationsLoaded) return
        await loadWarehouses()
        const lists = await Promise.all(warehouses.value.map((w) => ensureLocations(w.id)))
        locations.value = lists.flat()
        allLocationsLoaded = true
    }

    const productOptions = computed<SelectOption[]>(() =>
        products.value.map((p) => ({ label: p.sku ? `${p.name} (${p.sku})` : p.name, value: p.id }))
    )
    const warehouseOptions = computed<SelectOption[]>(() =>
        warehouses.value.map((w) => ({ label: w.name, value: w.id }))
    )

    const productNameById = computed(() =>
        Object.fromEntries(products.value.map((p) => [p.id, p.name]))
    )
    const warehouseNameById = computed(() =>
        Object.fromEntries(warehouses.value.map((w) => [w.id, w.name]))
    )
    const locationById = computed(() => Object.fromEntries(locations.value.map((l) => [l.id, l])))

    const shortId = (id: string) => id.slice(0, 8)

    function productName(id?: string | null): string {
        if (!id) return '—'
        return productNameById.value[id] ?? shortId(id)
    }

    /** Human label for a location id, e.g. "Main DC · Aisle 3" (needs locations loaded). */
    function locationLabel(id?: string | null): string {
        if (!id) return '—'
        const loc = locationById.value[id]
        if (!loc) return shortId(id)
        const wh = warehouseNameById.value[loc.warehouseId]
        return wh ? `${wh} · ${loc.name}` : loc.name
    }

    function locationOptionsForWarehouse(warehouseId?: string | null): SelectOption[] {
        if (!warehouseId) return []
        return (locationsByWarehouse.value[warehouseId] ?? []).map((l) => ({
            label: l.name,
            value: l.id,
        }))
    }

    return {
        products,
        warehouses,
        locations,
        locationsByWarehouse,
        loadProducts,
        loadWarehouses,
        ensureLocations,
        loadAllLocations,
        productOptions,
        warehouseOptions,
        productName,
        locationLabel,
        locationOptionsForWarehouse,
    }
}
