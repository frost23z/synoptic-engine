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
            def(CrmPermissions.TAGS_CREATE, "Create tags"),
            def(CrmPermissions.TAGS_EDIT, "Edit tags"),
            def(CrmPermissions.TAGS_DELETE, "Delete tags"),
            def(CrmPermissions.LEADS, "Manage leads"),
            def(CrmPermissions.LEADS_VIEW, "View leads"),
            def(CrmPermissions.LEADS_CREATE, "Create leads"),
            def(CrmPermissions.LEADS_EDIT, "Edit leads"),
            def(CrmPermissions.LEADS_DELETE, "Delete leads"),
            def(CrmPermissions.CONTACTS, "Manage contacts"),
            def(CrmPermissions.CONTACTS_VIEW, "View contacts"),
            def(CrmPermissions.CONTACTS_CREATE, "Create contacts"),
            def(CrmPermissions.CONTACTS_EDIT, "Edit contacts"),
            def(CrmPermissions.CONTACTS_DELETE, "Delete contacts"),
            def(CrmPermissions.ACTIVITIES, "Manage activities"),
            def(CrmPermissions.ACTIVITIES_VIEW, "View activities"),
            def(CrmPermissions.ACTIVITIES_CREATE, "Create activities"),
            def(CrmPermissions.ACTIVITIES_EDIT, "Edit activities"),
            def(CrmPermissions.ACTIVITIES_DELETE, "Delete activities"),
            def(CrmPermissions.QUOTES, "Manage quotes"),
            def(CrmPermissions.QUOTES_VIEW, "View quotes"),
            def(CrmPermissions.QUOTES_CREATE, "Create quotes"),
            def(CrmPermissions.QUOTES_EDIT, "Edit quotes"),
            def(CrmPermissions.QUOTES_DELETE, "Delete quotes"),
            def(CrmPermissions.MAIL, "Manage mail"),
            def(CrmPermissions.MAIL_VIEW, "View mail"),
            def(CrmPermissions.MAIL_EDIT, "Send and edit mail"),
            def(CrmPermissions.MAIL_INBOX, "View inbox folder"),
            def(CrmPermissions.MAIL_SENT, "View sent folder"),
            def(CrmPermissions.MAIL_DRAFTS, "View drafts folder"),
            def(CrmPermissions.MAIL_TRASH, "View trash folder"),
            def(CrmPermissions.MAIL_SPAM, "View spam folder"),
            def(CrmPermissions.MAIL_OUTBOX, "View outbox folder"),
            def(CrmPermissions.PIPELINES, "Manage pipelines"),
            def(CrmPermissions.PIPELINES_VIEW, "View pipelines"),
            def(CrmPermissions.PIPELINES_CREATE, "Create pipelines"),
            def(CrmPermissions.PIPELINES_EDIT, "Edit pipelines"),
            def(CrmPermissions.REPORTS, "View reports"),
            def(CrmPermissions.REPORTS_VIEW, "View dashboard and reports"),
        )

    private fun def(
        key: String,
        description: String,
    ) = PermissionDefinition(key = key, description = description, module = "crm")
}
