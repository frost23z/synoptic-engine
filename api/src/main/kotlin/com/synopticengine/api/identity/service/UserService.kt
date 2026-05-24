package com.synopticengine.api.identity.service

import com.synopticengine.api.identity.IdentityApi
import com.synopticengine.api.identity.UserCredentials
import com.synopticengine.api.identity.UserSummary
import com.synopticengine.api.identity.ViewContext
import com.synopticengine.api.identity.domain.User
import com.synopticengine.api.identity.domain.ViewPermission
import com.synopticengine.api.identity.repo.GroupRepository
import com.synopticengine.api.identity.repo.PermissionRepository
import com.synopticengine.api.identity.repo.RoleRepository
import com.synopticengine.api.identity.repo.UserRepository
import com.synopticengine.api.identity.web.UserDetailResponse
import com.synopticengine.api.shared.TenantContext
import com.synopticengine.api.shared.config.TenantSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val groupRepository: GroupRepository,
    private val permissionRepository: PermissionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tenantSession: TenantSession,
) : IdentityApi {
    override fun findById(id: UUID): UserSummary? =
        userRepository
            .findActiveById(id)
            ?.toSummary()

    override fun findAllActive(): List<UserSummary> = userRepository.findAllByDeletedAtIsNull().map { it.toSummary() }

    override fun existsById(id: UUID): Boolean = userRepository.existsActiveById(id)

    override fun findCredentialsByEmail(email: String): UserCredentials? =
        userRepository.findActiveByEmailWithRolesAsList(email).firstOrNull()?.toCredentials()

    override fun findCredentialsById(id: UUID): UserCredentials? =
        userRepository.findActiveByIdWithRolesAsList(id).firstOrNull()?.toCredentials()

    fun findDetailById(id: UUID): UserDetailResponse =
        userRepository
            .findActiveByIdWithRolesAsList(id)
            .firstOrNull()
            ?.toDetailResponse()
            ?: throw NoSuchElementException("User not found: $id")

    fun search(query: String): List<UserSummary> = userRepository.searchActive(query).map { it.toSummary() }

    @Transactional
    fun create(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String? = null,
        roleNames: Set<String> = setOf("SALESPERSON"),
        groupIds: Set<UUID> = emptySet(),
        viewPermission: ViewPermission = ViewPermission.GLOBAL,
    ): UserDetailResponse {
        // Ensure subsequent role/group/email lookups stay inside the current tenant,
        // even on code paths that don't go through TenantFilterInterceptor (programmatic
        // provisioning, scheduled jobs, tests). Otherwise a role name like "ADMIN"
        // matches every tenant's ADMIN row and the new user gets multi-tenant role
        // membership — see analysis/07-verification-findings.md P0-2.
        tenantSession.applyFilter()
        if (userRepository.existsByEmail(email)) throw IllegalStateException("Email already in use: $email")
        val roles = roleRepository.findAllByNameIn(roleNames)
        if (roles.isEmpty()) throw IllegalArgumentException("No valid roles found: $roleNames")
        val groups = if (groupIds.isNotEmpty()) groupRepository.findAllByIdIn(groupIds) else emptyList()

        val user =
            userRepository.save(
                User().apply {
                    this.email = email
                    this.passwordHash = passwordEncoder.encode(password) ?: error("Password encoding failed")
                    this.firstName = firstName
                    this.lastName = lastName
                    this.phone = phone
                    this.viewPermission = viewPermission
                    this.roles.addAll(roles)
                    this.groups.addAll(groups)
                },
            )
        return user.toDetailResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        firstName: String,
        lastName: String,
        phone: String?,
        roleNames: Set<String>?,
        groupIds: Set<UUID>?,
        viewPermission: ViewPermission?,
    ): UserDetailResponse {
        val user = requireUser(id)
        user.firstName = firstName
        user.lastName = lastName
        user.phone = phone
        if (viewPermission != null) user.viewPermission = viewPermission
        if (roleNames != null) {
            val roles = roleRepository.findAllByNameIn(roleNames)
            if (roles.isEmpty()) throw IllegalArgumentException("No valid roles found: $roleNames")
            user.roles.clear()
            user.roles.addAll(roles)
        }
        if (groupIds != null) {
            val groups = groupRepository.findAllByIdIn(groupIds)
            user.groups.clear()
            user.groups.addAll(groups)
        }
        return userRepository.save(user).toDetailResponse()
    }

    @Transactional
    fun deactivate(id: UUID) {
        val user = requireUser(id)
        if (id == currentUserId()) {
            throw IllegalStateException("You cannot deactivate your own account.")
        }
        ensureNotLastAdmin(user)
        user.isActive = false
        userRepository.save(user)
    }

    @Transactional
    fun setPassword(
        id: UUID,
        newPassword: String,
    ) {
        val user = requireUser(id)
        user.passwordHash = passwordEncoder.encode(newPassword) ?: error("Password encoding failed")
        userRepository.save(user)
    }

    @Transactional
    fun setActiveStatus(
        ids: List<UUID>,
        isActive: Boolean,
    ) {
        val selfId = currentUserId()
        ids.forEach { id ->
            val user = requireUser(id)
            if (!isActive && id == selfId) {
                throw IllegalStateException("You cannot deactivate your own account.")
            }
            if (!isActive) {
                ensureNotLastAdmin(user)
            }
            user.isActive = isActive
            userRepository.save(user)
        }
    }

    @Transactional
    fun massDeactivate(ids: List<UUID>) {
        val selfId = currentUserId()
        val toDeactivate = ids.filter { it != selfId }
        toDeactivate.forEach { id ->
            // findActiveByIdWithRolesAsList runs JPQL, so the tenant filter
            // applies and a caller cannot mass-deactivate users from another
            // tenant by passing their UUIDs.
            userRepository.findActiveByIdWithRolesAsList(id).firstOrNull()?.let { user ->
                if (runCatching { ensureNotLastAdmin(user) }.isSuccess) {
                    user.isActive = false
                    userRepository.save(user)
                }
            }
        }
    }

    private fun ensureNotLastAdmin(user: User) {
        if (user.roles.none { it.name == "ADMIN" }) return
        val activeAdmins =
            userRepository.findAllByDeletedAtIsNull().count { other ->
                other.id != user.id && other.roles.any { it.name == "ADMIN" }
            }
        if (activeAdmins == 0) {
            throw IllegalStateException("Cannot deactivate the last active ADMIN. Promote another user first.")
        }
    }

    private fun currentUserId(): UUID? {
        val email =
            org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .authentication
                ?.name
                ?: return null
        return userRepository.findActiveByEmail(email)?.id
    }

    @Transactional
    override fun updatePassword(
        email: String,
        encodedPassword: String,
    ) {
        val user =
            userRepository.findActiveByEmail(email)
                ?: throw NoSuchElementException("User not found: $email")
        user.passwordHash = encodedPassword
        userRepository.save(user)
    }

    override fun resolveViewContextByEmail(email: String): ViewContext {
        val userId =
            userRepository.findActiveByEmailWithRolesAsList(email).firstOrNull()?.id ?: return ViewContext(null)
        return resolveViewContext(userId)
    }

    override fun findFirstActiveUserInGroup(groupId: UUID): UUID? {
        val tenantId = TenantContext.get() ?: return null
        return userRepository.findActiveIdsByGroupId(groupId, tenantId).firstOrNull()
    }

    override fun resolveViewContext(requesterId: UUID): ViewContext {
        val user =
            userRepository.findActiveById(requesterId)
                ?: return ViewContext(null)
        return when (user.viewPermission) {
            ViewPermission.ALL, ViewPermission.GLOBAL -> {
                ViewContext(null)
            }

            ViewPermission.GROUP -> {
                // `findGroupMemberIds` is a native query that joins through
                // user_groups; the tenant predicate is the only isolation layer
                // because Hibernate's `@Filter` does not rewrite native SQL.
                val tenantId = TenantContext.get() ?: user.tenantId
                val ids = userRepository.findGroupMemberIds(requesterId, tenantId).toSet()
                ViewContext(ids.ifEmpty { setOf(requesterId) })
            }

            ViewPermission.INDIVIDUAL -> {
                ViewContext(setOf(requesterId))
            }
        }
    }

    // Tenant-aware load. JpaRepository.findById bypasses Hibernate's
    // `@Filter("tenantFilter")` (EntityManager.find() fast path); the
    // findActiveByIdWithRolesAsList JPQL query does not, so cross-tenant
    // fetches return empty here and surface as 404 at the controller.
    private fun requireUser(id: UUID): User =
        userRepository.findActiveByIdWithRolesAsList(id).firstOrNull()
            ?: throw NoSuchElementException("User not found: $id")

    private fun User.toSummary() =
        UserSummary(
            id = id!!,
            email = email,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun User.toCredentials(): UserCredentials {
        val allKnownKeys = permissionRepository.findAllKeys()
        val authorities =
            if (roles.any { it.permissionType == com.synopticengine.api.identity.domain.RoleType.ALL }) {
                allKnownKeys.toList()
            } else {
                expandAuthorities(
                    roles.flatMap { it.permissions }.map { it.key },
                    allKnownKeys,
                )
            }
        return UserCredentials(
            id = id!!,
            tenantId = tenantId,
            email = email,
            fullName = fullName,
            passwordHash = passwordHash,
            isActive = isActive,
            deletedAt = deletedAt,
            authorities = authorities,
        )
    }

    private fun User.toDetailResponse(): UserDetailResponse =
        UserDetailResponse(
            id = id!!,
            email = email,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            phone = phone,
            isActive = isActive,
            viewPermission = viewPermission.name,
            roles = roles.map { it.name },
            groups =
                groups.map { it.id!! to it.name }.map { (id, name) ->
                    mapOf("id" to id.toString(), "name" to name)
                },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

fun expandAuthorities(
    keys: Collection<String>,
    allKnownKeys: Set<String>,
): List<String> {
    val result = mutableSetOf<String>()
    for (key in keys) {
        result.add(key)
        allKnownKeys.filter { it.startsWith("$key.") }.forEach { result.add(it) }
    }
    return result.toList()
}
