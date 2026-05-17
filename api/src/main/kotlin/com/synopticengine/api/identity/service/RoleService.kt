package com.synopticengine.api.identity.service

import com.synopticengine.api.identity.domain.Permission
import com.synopticengine.api.identity.domain.Role
import com.synopticengine.api.identity.repo.PermissionRepository
import com.synopticengine.api.identity.repo.RoleRepository
import com.synopticengine.api.identity.web.PermissionResponse
import com.synopticengine.api.identity.web.RoleResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
) {
    fun findAll(): List<RoleResponse> = roleRepository.findAll().map { it.toResponse() }

    fun findAllPermissions(): List<PermissionResponse> = permissionRepository.findAll().map { it.toResponse() }

    fun findById(id: UUID): RoleResponse = requireRole(id).toResponse()

    @Transactional
    fun create(
        name: String,
        description: String?,
        permissionNames: Set<String>,
    ): RoleResponse {
        if (roleRepository.findByName(name) != null) throw IllegalStateException("Role name already in use: $name")
        val permissions = resolvePermissions(permissionNames)
        val role =
            Role().apply {
                this.name = name
                this.description = description
                this.permissions.addAll(permissions)
            }
        return roleRepository.save(role).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        name: String,
        description: String?,
        permissionNames: Set<String>,
    ): RoleResponse {
        val role = requireRole(id)
        val existing = roleRepository.findByName(name)
        if (existing != null && existing.id != id) throw IllegalStateException("Role name already in use: $name")
        val permissions = resolvePermissions(permissionNames)
        role.name = name
        role.description = description
        role.permissions.clear()
        role.permissions.addAll(permissions)
        return roleRepository.save(role).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        if (!roleRepository.existsById(id)) throw NoSuchElementException("Role not found: $id")
        roleRepository.deleteById(id)
    }

    private fun resolvePermissions(names: Set<String>): List<Permission> {
        if (names.isEmpty()) return emptyList()
        return permissionRepository.findAllByKeyIn(names)
    }

    private fun requireRole(id: UUID): Role =
        roleRepository
            .findById(id)
            .orElseThrow { NoSuchElementException("Role not found: $id") }
}

fun Role.toResponse() =
    RoleResponse(
        id = id!!,
        name = name,
        description = description,
        permissions = permissions.map { it.key },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Permission.toResponse() =
    PermissionResponse(
        id = id!!,
        name = key,
        description = description,
    )
