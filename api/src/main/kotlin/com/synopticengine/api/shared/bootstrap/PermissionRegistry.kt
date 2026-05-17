package com.synopticengine.api.shared.bootstrap

interface PermissionRegistry {
    fun permissions(): List<PermissionDefinition>
}
