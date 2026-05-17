package com.synopticengine.api.identity

/** Public API used by the bootstrap module to seed permissions, roles, and the admin user. */
interface BootstrapPort {
    /** Ensure a permission with the given name exists. Idempotent. */
    fun ensurePermission(
        name: String,
        description: String?,
    )

    /** Return all permission names currently in the database. */
    fun allPermissionNames(): Set<String>

    /**
     * Create or update a role with the given permissions.
     * Permissions are identified by name. Unknown names are silently ignored.
     *
     * When [wildcardPermissions] is true the role gets `permission_type = ALL` — it
     * implicitly holds every permission key in the catalog, including any added in the
     * future. The [permissionNames] argument is ignored in that case.
     */
    fun upsertRole(
        name: String,
        description: String,
        permissionNames: Set<String>,
        wildcardPermissions: Boolean = false,
    )

    /**
     * Create the admin user if it does not already exist.
     * [encodedPassword] must already be bcrypt-hashed by the caller.
     */
    fun ensureAdminUser(
        email: String,
        encodedPassword: String,
        adminRoleName: String,
    )
}
