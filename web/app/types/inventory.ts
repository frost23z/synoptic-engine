import type { PageResponse } from './api'
import type { TagResponse } from './leads'

// ── Generated DTOs (source of truth, from OpenAPI `../api-docs.json`) ────────
// Re-exported under this module path so existing `~/types/inventory` imports
// keep working while the underlying shapes now flow from the backend spec.
export type {
    InventoryEntryResponse,
    LowStockEntry,
    StockStateResponse,
    TransferOrderResponse,
    WarehouseLocationResponse,
    WarehouseProductEntry,
} from '~/api/types.gen'

type BadgeColor = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error' | 'neutral'

// ── Still hand-written, pending Tag-type unification ────────────────────────
// Generated `ProductResponse`/`WarehouseResponse` carry `tags: TagDto[]`
// (color optional, no createdAt), but our tag UI (`AppTagManager`) consumes the
// richer `TagResponse`. Unify TagDto↔TagResponse before bridging these two.
export interface ProductResponse {
    id: string
    name: string
    description?: string
    price: number
    sku?: string
    isActive: boolean
    reorderThreshold?: number
    createdAt: string
    updatedAt: string
}

export interface WarehouseResponse {
    id: string
    name: string
    description?: string
    contactName?: string
    contactEmail?: string
    contactPhone?: string
    contactAddress?: string
    tags: TagResponse[]
    createdAt: string
    updatedAt: string
}

export type ProductsPage = PageResponse<ProductResponse>
export type WarehousesPage = PageResponse<WarehouseResponse>

// ── Transfer order status (UI label/colour maps — presentation, not in spec) ─
export type TransferStatus = 'PENDING' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED'

export const TRANSFER_STATUS_LABEL: Record<TransferStatus, string> = {
    PENDING: 'Pending',
    IN_TRANSIT: 'In transit',
    COMPLETED: 'Completed',
    CANCELLED: 'Cancelled',
}

export const TRANSFER_STATUS_COLOR: Record<TransferStatus, BadgeColor> = {
    PENDING: 'neutral',
    IN_TRANSIT: 'info',
    COMPLETED: 'success',
    CANCELLED: 'error',
}

// ── Movement ledger ─────────────────────────────────────────────────────────
// TEMP hand-written: the `GET /api/inventory/movements` endpoint is not yet in
// the committed `api-docs.json` snapshot. Once the spec is regenerated from the
// backend, drop this block and re-export `MovementResponse` from `~/api/types.gen`.
export type MovementType =
    | 'RECEIPT'
    | 'ISSUE'
    | 'ADJUST'
    | 'TRANSFER_IN'
    | 'TRANSFER_OUT'
    | 'RESERVE'
    | 'RELEASE'

export const MOVEMENT_TYPE_LABEL: Record<MovementType, string> = {
    RECEIPT: 'Receipt',
    ISSUE: 'Issue',
    ADJUST: 'Adjustment',
    TRANSFER_IN: 'Transfer in',
    TRANSFER_OUT: 'Transfer out',
    RESERVE: 'Reserved',
    RELEASE: 'Released',
}

export const MOVEMENT_TYPE_COLOR: Record<MovementType, BadgeColor> = {
    RECEIPT: 'success',
    ISSUE: 'warning',
    ADJUST: 'neutral',
    TRANSFER_IN: 'info',
    TRANSFER_OUT: 'info',
    RESERVE: 'secondary',
    RELEASE: 'primary',
}

export interface MovementResponse {
    id: string
    productId: string
    movementType: MovementType
    fromLocationId?: string | null
    toLocationId?: string | null
    quantity: number
    unitCost?: number | null
    refDocType?: string | null
    refDocId?: string | null
    actorId?: string | null
    notes?: string | null
    createdAt?: string | null
}
