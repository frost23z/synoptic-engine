package com.synopticengine.api.identity.service

import com.synopticengine.api.identity.BootstrapPort
import com.synopticengine.api.identity.domain.Permission
import com.synopticengine.api.identity.domain.Role
import com.synopticengine.api.identity.domain.User
import com.synopticengine.api.identity.domain.ViewPermission
import com.synopticengine.api.identity.repo.PermissionRepository
import com.synopticengine.api.identity.repo.RoleRepository
import com.synopticengine.api.identity.repo.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
internal class BootstrapPortImpl(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) : BootstrapPort {
    override fun ensurePermission(
        name: String,
        description: String?,
    ) {
        if (permissionRepository.findAllByKeyIn(listOf(name)).isEmpty()) {
            permissionRepository.save(
                Permission().apply {
                    this.key = name
                    this.description = description
                },
            )
        }
    }

    override fun allPermissionNames(): Set<String> = permissionRepository.findAll().map { it.key }.toSet()

    override fun upsertRole(
        name: String,
        description: String,
        permissionNames: Set<String>,
    ) {
        val permissions = permissionRepository.findAllByKeyIn(permissionNames).toMutableSet()
        val existing = roleRepository.findByName(name)
        if (existing == null) {
            roleRepository.save(
                Role().apply {
                    this.name = name
                    this.description = description
                    this.permissions.addAll(permissions)
                },
            )
        } else {
            existing.description = description
            existing.permissions.clear()
            existing.permissions.addAll(permissions)
            roleRepository.save(existing)
        }
    }

    override fun ensureAdminUser(
        email: String,
        encodedPassword: String,
        adminRoleName: String,
    ) {
        if (userRepository.existsByEmail(email)) return
        val adminRole =
            roleRepository.findByName(adminRoleName)
                ?: error("Role '$adminRoleName' not found; ensure it is upserted before creating the admin user")
        userRepository.save(
            User().apply {
                this.email = email
                this.passwordHash = encodedPassword
                this.firstName = "Admin"
                this.lastName = "User"
                this.isActive = true
                this.viewPermission = ViewPermission.GLOBAL
                this.roles.add(adminRole)
            },
        )
    }
}
