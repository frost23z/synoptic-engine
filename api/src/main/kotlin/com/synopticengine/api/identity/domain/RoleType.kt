package com.synopticengine.api.identity.domain

/**
 * `ALL` roles bypass permission checks — they implicitly hold every permission key in the
 * catalog, including ones added in the future. `CUSTOM` roles only hold the explicit
 * permissions in their `permissions` relation.
 */
enum class RoleType {
    ALL,
    CUSTOM,
}
