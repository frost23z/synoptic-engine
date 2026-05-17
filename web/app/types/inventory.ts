import type { PageResponse } from './api'

export interface ProductResponse {
    id: string
    name: string
    description?: string
    price: number
    sku?: string
    active: boolean
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

export interface WarehouseTagResponse {
    id: string
    name: string
}
