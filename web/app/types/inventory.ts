import type { PageResponse } from './api'
import type { TagResponse } from './leads'

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

export interface WarehouseLocationResponse {
    id: string
    warehouseId: string
    name: string
    createdAt: string
    updatedAt: string
}

export interface InventoryEntryResponse {
    id: string
    productId: string
    warehouseId: string
    warehouseLocationId?: string
    quantity: number
}

export interface WarehouseProductEntry {
    productId: string
    warehouseLocationId?: string
    quantity: number
}

export type ProductsPage = PageResponse<ProductResponse>
export type WarehousesPage = PageResponse<WarehouseResponse>

// ── Inventory movements / stock ────────────────────────────────────────────
/** Per-location stock state for a product (`GET /api/inventory/stock`). */
export interface StockStateResponse {
    productId: string
    locationId?: string | null
    warehouseId: string
    onHand: number
    reserved: number
    inTransit: number
    damaged: number
    available: number
}

/** A product at or below its reorder threshold (`GET /api/inventory/low-stock`). */
export interface LowStockEntry {
    productId: string
    productName: string
    sku?: string | null
    reorderThreshold: number
    currentStock: number
}

// ── Movement ledger ────────────────────────────────────────────────────────
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

/** An append-only ledger entry (`GET /api/inventory/movements`). */
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

// ── Transfer orders ────────────────────────────────────────────────────────
export type TransferStatus = 'PENDING' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED'

type BadgeColor = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error' | 'neutral'

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

export interface TransferOrderResponse {
    id: string
    fromLocationId: string
    toLocationId: string
    productId: string
    quantity: number
    status: TransferStatus
    outMovementId?: string | null
    inMovementId?: string | null
    notes?: string | null
    createdAt?: string | null
    updatedAt?: string | null
}
