package com.synopticengine.api.sharing

import com.synopticengine.api.shared.bootstrap.PermissionDefinition
import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.springframework.stereotype.Component

@Component
class SharingPermissionRegistry : PermissionRegistry {
    override fun permissions(): List<PermissionDefinition> =
        listOf(
            def(SharingPermissions.RELATIONSHIPS, "Manage tenant relationships"),
            def(SharingPermissions.RELATIONSHIPS_VIEW, "View tenant relationships"),
            def(SharingPermissions.RELATIONSHIPS_MANAGE, "Initiate, accept, and revoke relationships"),
            def(SharingPermissions.SHARE_POLICIES, "Manage share policies"),
            def(SharingPermissions.SHARE_POLICIES_VIEW, "View share policies"),
            def(SharingPermissions.SHARE_POLICIES_MANAGE, "Create and revoke share policies"),
            def(SharingPermissions.RECORDS, "Manage cross-tenant record actions"),
            def(SharingPermissions.RECORDS_SHARE, "Share an individual record with another tenant"),
            def(SharingPermissions.RECORDS_RESHARE, "Reshare a record received via share (MANAGE only)"),
        )

    private fun def(
        key: String,
        description: String,
    ) = PermissionDefinition(key = key, description = description, module = "sharing")
}
