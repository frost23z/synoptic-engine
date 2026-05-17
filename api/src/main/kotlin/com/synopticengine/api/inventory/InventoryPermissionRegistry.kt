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
            def(InventoryPermissions.PRODUCTS_EDIT, "Create and edit products"),
            def(InventoryPermissions.PRODUCTS_DELETE, "Delete products"),
            def(InventoryPermissions.WAREHOUSES, "Manage warehouses"),
            def(InventoryPermissions.WAREHOUSES_VIEW, "View warehouses"),
            def(InventoryPermissions.WAREHOUSES_EDIT, "Create and edit warehouses"),
            def(InventoryPermissions.WAREHOUSES_DELETE, "Delete warehouses"),
        )

    private fun def(
        key: String,
        description: String,
    ) = PermissionDefinition(key = key, description = description, module = "inventory")
}
