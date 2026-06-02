package com.synopticengine.api.identity.service

import com.synopticengine.api.identity.domain.Permission
import com.synopticengine.api.identity.domain.Role
import com.synopticengine.api.identity.repo.PermissionRepository
import com.synopticengine.api.identity.repo.RoleRepository
import com.synopticengine.api.identity.web.PermissionResponse
import com.synopticengine.api.identity.web.RoleResponse
import com.synopticengine.api.shared.audit.AuditAction
import com.synopticengine.api.shared.audit.AuditLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val auditLogService: AuditLogService,
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
        val saved = roleRepository.save(role)
        auditLogService.record(
            entityType = "role",
            entityId = saved.id.toString(),
            action = AuditAction.CREATE,
            payload = mapOf("roleName" to name, "permissions" to permissionNames.sorted()),
        )
        return saved.toResponse()
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
        val oldKeys = role.permissions.map { it.key }.toSet()
        val permissions = resolvePermissions(permissionNames)
        role.name = name
        role.description = description
        role.permissions.clear()
        role.permissions.addAll(permissions)
        val saved = roleRepository.save(role)
        val added = permissionNames - oldKeys
        val removed = oldKeys - permissionNames
        if (added.isNotEmpty() || removed.isNotEmpty()) {
            auditLogService.record(
                entityType = "role",
                entityId = id.toString(),
                action = AuditAction.UPDATE,
                payload =
                    buildMap {
                        put("roleName", name)
                        if (added.isNotEmpty()) put("permissionsAdded", added.sorted())
                        if (removed.isNotEmpty()) put("permissionsRemoved", removed.sorted())
                    },
            )
        }
        return saved.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        // Load via the tenant-aware finder; then delete the entity (not by id)
        // so the filter has actually run and we cannot delete a role from
        // another tenant.
        val role = requireRole(id)
        auditLogService.record(
            entityType = "role",
            entityId = id.toString(),
            action = AuditAction.DELETE,
            payload = mapOf("roleName" to role.name),
        )
        roleRepository.delete(role)
    }

    private fun resolvePermissions(names: Set<String>): List<Permission> {
        if (names.isEmpty()) return emptyList()
        return permissionRepository.findAllByKeyIn(names)
    }

    // Tenant-aware load. JpaRepository.findById bypasses Hibernate's
    // `@Filter("tenantFilter")` (EntityManager.find() fast path); the
    // findActiveById JPQL on RoleRepository does not, so cross-tenant fetches
    // return null and surface as 404 at the controller.
    private fun requireRole(id: UUID): Role =
        roleRepository.findActiveById(id) ?: throw NoSuchElementException("Role not found: $id")
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
