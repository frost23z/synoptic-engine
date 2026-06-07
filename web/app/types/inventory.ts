import type { ProductResponse, WarehouseResponse } from '~/api/types.gen'
import type { PageResponse } from './api'

// ── Generated DTOs (source of truth, from OpenAPI `../api-docs.json`) ────────
// Re-exported under this module path so existing `~/types/inventory` imports
// keep working while the underlying shapes now flow from the backend spec.
// `ProductResponse`/`WarehouseResponse` are now bridged too: the embedded
// `tags: TagDto[]` they carry is consumed by `AppTagManager`, whose props were
// widened to the minimal `TagDto` shape (Tag-type unification, drift item #1).
export type {
    InventoryEntryResponse,
    LowStockEntry,
    MovementResponse,
    ProductResponse,
    StockStateResponse,
    TransferOrderResponse,
    WarehouseLocationResponse,
    WarehouseProductEntry,
    WarehouseResponse,
} from '~/api/types.gen'

type BadgeColor = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error' | 'neutral'

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
// `MovementResponse` is re-exported from the generated types above. The
// label/colour maps below are presentation concerns (not in the spec); the union
// mirrors `MovementResponse.movementType`.
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
