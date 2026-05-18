package com.synopticengine.api.settings

import com.synopticengine.api.shared.bootstrap.PermissionDefinition
import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.springframework.stereotype.Component

@Component
class SettingsPermissionRegistry : PermissionRegistry {
    override fun permissions(): List<PermissionDefinition> =
        listOf(
            def(SettingsPermissions.SETTINGS, "Manage system settings"),
            def(SettingsPermissions.SETTINGS_VIEW, "View system settings"),
            def(SettingsPermissions.SETTINGS_EDIT, "Edit system settings"),
            def(SettingsPermissions.ATTRIBUTES, "Manage custom attributes"),
            def(SettingsPermissions.ATTRIBUTES_VIEW, "View attributes"),
            def(SettingsPermissions.ATTRIBUTES_CREATE, "Create attributes"),
            def(SettingsPermissions.ATTRIBUTES_EDIT, "Edit attributes"),
            def(SettingsPermissions.ATTRIBUTES_DELETE, "Delete attributes"),
            def(SettingsPermissions.AUTOMATIONS, "Manage automations"),
            def(SettingsPermissions.AUTOMATIONS_VIEW, "View automations"),
            def(SettingsPermissions.AUTOMATIONS_CREATE, "Create automations"),
            def(SettingsPermissions.AUTOMATIONS_EDIT, "Edit automations"),
            def(SettingsPermissions.AUTOMATIONS_DELETE, "Delete automations"),
            def(SettingsPermissions.MARKETING, "Manage marketing"),
            def(SettingsPermissions.MARKETING_VIEW, "View marketing campaigns"),
            def(SettingsPermissions.MARKETING_CREATE, "Create marketing campaigns and events"),
            def(SettingsPermissions.MARKETING_EDIT, "Edit campaigns"),
            def(SettingsPermissions.MARKETING_DELETE, "Delete campaigns"),
            def(SettingsPermissions.IMPORTS, "Manage data imports"),
            def(SettingsPermissions.IMPORTS_VIEW, "View import history"),
            def(SettingsPermissions.IMPORTS_CREATE, "Create data imports"),
            def(SettingsPermissions.IMPORTS_EDIT, "Run data imports"),
        )

    private fun def(
        key: String,
        description: String,
    ) = PermissionDefinition(key = key, description = description, module = "settings")
}
