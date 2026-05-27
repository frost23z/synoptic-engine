package com.synopticengine.api.inventory

import com.synopticengine.api.shared.bootstrap.PermissionDefinition
import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.springframework.stereotype.Component

@Component
class InventoryPermissionRegistry : PermissionRegistry {
    override fun permissions(): List<PermissionDefinition> =
        listOf(
            def(InventoryPermissions.PRODUCTS, "Manage products"),
            def(InventoryPermissions.PRODUCTS_VIEW, "View products"),
            def(InventoryPermissions.PRODUCTS_CREATE, "Create products"),
            def(InventoryPermissions.PRODUCTS_EDIT, "Edit products"),
            def(InventoryPermissions.PRODUCTS_DELETE, "Delete products"),
            def(InventoryPermissions.WAREHOUSES, "Manage warehouses"),
            def(InventoryPermissions.WAREHOUSES_VIEW, "View warehouses"),
            def(InventoryPermissions.WAREHOUSES_CREATE, "Create warehouses"),
            def(InventoryPermissions.WAREHOUSES_EDIT, "Edit warehouses"),
            def(InventoryPermissions.WAREHOUSES_DELETE, "Delete warehouses"),
            def(InventoryPermissions.INVENTORY_MOVEMENTS, "Manage inventory movements"),
            def(InventoryPermissions.INVENTORY_MOVEMENTS_VIEW, "View inventory movements and stock states"),
            def(InventoryPermissions.INVENTORY_MOVEMENTS_CREATE, "Create inventory movements and reservations"),
            def(InventoryPermissions.INVENTORY_TRANSFERS, "Manage transfer orders"),
            def(InventoryPermissions.INVENTORY_TRANSFERS_VIEW, "View transfer orders"),
            def(InventoryPermissions.INVENTORY_TRANSFERS_CREATE, "Create transfer orders"),
            def(InventoryPermissions.INVENTORY_TRANSFERS_MANAGE, "Dispatch, receive, and cancel transfer orders"),
            def(InventoryPermissions.INVENTORY_REORDER_VIEW, "View low-stock reorder alerts"),
        )

    private fun def(
        key: String,
        description: String,
    ) = PermissionDefinition(key = key, description = description, module = "inventory")
}
