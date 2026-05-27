package com.synopticengine.api.inventory

object InventoryPermissions {
    const val PRODUCTS = "products"
    const val PRODUCTS_VIEW = "products.view"
    const val PRODUCTS_CREATE = "products.create"
    const val PRODUCTS_EDIT = "products.edit"
    const val PRODUCTS_DELETE = "products.delete"

    const val WAREHOUSES = "warehouses"
    const val WAREHOUSES_VIEW = "warehouses.view"
    const val WAREHOUSES_CREATE = "warehouses.create"
    const val WAREHOUSES_EDIT = "warehouses.edit"
    const val WAREHOUSES_DELETE = "warehouses.delete"

    const val INVENTORY_MOVEMENTS = "inventory.movements"
    const val INVENTORY_MOVEMENTS_VIEW = "inventory.movements.view"
    const val INVENTORY_MOVEMENTS_CREATE = "inventory.movements.create"

    const val INVENTORY_TRANSFERS = "inventory.transfers"
    const val INVENTORY_TRANSFERS_VIEW = "inventory.transfers.view"
    const val INVENTORY_TRANSFERS_CREATE = "inventory.transfers.create"
    const val INVENTORY_TRANSFERS_MANAGE = "inventory.transfers.manage"

    const val INVENTORY_REORDER_VIEW = "inventory.reorder.view"
}
