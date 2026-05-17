package com.synopticengine.api.crm

import com.synopticengine.api.shared.bootstrap.PermissionDefinition
import com.synopticengine.api.shared.bootstrap.PermissionRegistry
import org.springframework.stereotype.Component

@Component
class CrmPermissionRegistry : PermissionRegistry {
    override fun permissions(): List<PermissionDefinition> =
        listOf(
            def(CrmPermissions.TAGS, "Manage tags"),
            def(CrmPermissions.TAGS_VIEW, "View tags"),
            def(CrmPermissions.TAGS_EDIT, "Create and edit tags"),
            def(CrmPermissions.TAGS_DELETE, "Delete tags"),
            def(CrmPermissions.LEADS, "Manage leads"),
            def(CrmPermissions.LEADS_VIEW, "View leads"),
            def(CrmPermissions.LEADS_EDIT, "Create and edit leads"),
            def(CrmPermissions.LEADS_DELETE, "Delete leads"),
            def(CrmPermissions.CONTACTS, "Manage contacts"),
            def(CrmPermissions.CONTACTS_VIEW, "View contacts"),
            def(CrmPermissions.CONTACTS_EDIT, "Create and edit contacts"),
            def(CrmPermissions.CONTACTS_DELETE, "Delete contacts"),
            def(CrmPermissions.ACTIVITIES, "Manage activities"),
            def(CrmPermissions.ACTIVITIES_VIEW, "View activities"),
            def(CrmPermissions.ACTIVITIES_EDIT, "Create and edit activities"),
            def(CrmPermissions.ACTIVITIES_DELETE, "Delete activities"),
            def(CrmPermissions.QUOTES, "Manage quotes"),
            def(CrmPermissions.QUOTES_VIEW, "View quotes"),
            def(CrmPermissions.QUOTES_EDIT, "Create and edit quotes"),
            def(CrmPermissions.QUOTES_DELETE, "Delete quotes"),
            def(CrmPermissions.MAIL, "Manage mail"),
            def(CrmPermissions.MAIL_VIEW, "View mail"),
            def(CrmPermissions.MAIL_EDIT, "Send and edit mail"),
            def(CrmPermissions.PIPELINES, "Manage pipelines"),
            def(CrmPermissions.PIPELINES_VIEW, "View pipelines"),
            def(CrmPermissions.PIPELINES_EDIT, "Create and edit pipelines"),
            def(CrmPermissions.REPORTS, "View reports"),
            def(CrmPermissions.REPORTS_VIEW, "View dashboard and reports"),
        )

    private fun def(
        key: String,
        description: String,
    ) = PermissionDefinition(key = key, description = description, module = "crm")
}
