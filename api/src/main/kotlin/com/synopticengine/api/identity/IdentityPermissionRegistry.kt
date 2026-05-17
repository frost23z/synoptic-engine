package com.synopticengine.api.identity

import com.synopticengine.api.shared.bootstrap.PermissionDefinition
import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.springframework.stereotype.Component

@Component
class IdentityPermissionRegistry : PermissionRegistry {
    override fun permissions(): List<PermissionDefinition> =
        listOf(
            def(IdentityPermissions.USERS, "Manage users"),
            def(IdentityPermissions.USERS_VIEW, "View users"),
            def(IdentityPermissions.USERS_EDIT, "Create and edit users"),
            def(IdentityPermissions.USERS_DELETE, "Delete users"),
            def(IdentityPermissions.ROLES, "Manage roles"),
            def(IdentityPermissions.ROLES_VIEW, "View roles and permissions"),
            def(IdentityPermissions.ROLES_EDIT, "Create, edit, and delete roles"),
            def(IdentityPermissions.GROUPS, "Manage groups"),
            def(IdentityPermissions.GROUPS_VIEW, "View groups"),
            def(IdentityPermissions.GROUPS_EDIT, "Create and edit groups"),
            def(IdentityPermissions.GROUPS_DELETE, "Delete groups"),
        )

    private fun def(
        key: String,
        description: String,
    ) = PermissionDefinition(key = key, description = description, module = "identity")
}
