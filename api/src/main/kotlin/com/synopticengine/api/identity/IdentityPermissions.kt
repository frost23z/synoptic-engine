package com.synopticengine.api.identity

object IdentityPermissions {
    const val USERS = "users"
    const val USERS_VIEW = "users.view"
    const val USERS_EDIT = "users.edit"
    const val USERS_DELETE = "users.delete"

    const val ROLES = "roles"
    const val ROLES_VIEW = "roles.view"
    const val ROLES_EDIT = "roles.edit"

    const val GROUPS = "groups"
    const val GROUPS_VIEW = "groups.view"
    const val GROUPS_EDIT = "groups.edit"
    const val GROUPS_DELETE = "groups.delete"

    const val TENANTS = "tenants"
    const val TENANTS_VIEW = "tenants.view"
    const val TENANTS_MANAGE = "tenants.manage"
}
