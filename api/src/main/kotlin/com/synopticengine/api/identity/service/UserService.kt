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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val groupRepository: GroupRepository,
    private val permissionRepository: PermissionRepository,
    private val passwordEncoder: PasswordEncoder,
) : IdentityApi {
    override fun findById(id: UUID): UserSummary? =
        userRepository
            .findById(id)
            .orElse(null)
            ?.takeIf { it.deletedAt == null }
            ?.toSummary()

    override fun findAllActive(): List<UserSummary> = userRepository.findAllByDeletedAtIsNull().map { it.toSummary() }

    override fun existsById(id: UUID): Boolean = userRepository.existsById(id)

    override fun findCredentialsByEmail(email: String): UserCredentials? =
        userRepository.findActiveByEmailWithRoles(email)?.toCredentials()

    override fun findCredentialsById(id: UUID): UserCredentials? =
        userRepository.findActiveByIdWithRoles(id)?.toCredentials()

    fun findDetailById(id: UUID): UserDetailResponse =
        userRepository
            .findActiveByIdWithRoles(id)
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
        user.isActive = false
        user.deletedAt = Instant.now()
        userRepository.save(user)
    }

    @Transactional
    fun massDeactivate(ids: List<UUID>) {
        ids.forEach { id ->
            userRepository.findById(id).ifPresent { user ->
                if (user.deletedAt == null) {
                    user.isActive = false
                    user.deletedAt = Instant.now()
                    userRepository.save(user)
                }
            }
        }
    }

    @Transactional
    override fun updatePassword(
        email: String,
        encodedPassword: String,
    ) {
        val user =
            userRepository.findByEmail(email)
                ?: throw NoSuchElementException("User not found: $email")
        user.passwordHash = encodedPassword
        userRepository.save(user)
    }

    override fun resolveViewContextByEmail(email: String): ViewContext {
        val userId = userRepository.findActiveByEmailWithRoles(email)?.id ?: return ViewContext(null)
        return resolveViewContext(userId)
    }

    override fun resolveViewContext(requesterId: UUID): ViewContext {
        val user =
            userRepository.findById(requesterId).orElse(null)
                ?: return ViewContext(null)
        return when (user.viewPermission) {
            ViewPermission.ALL, ViewPermission.GLOBAL -> {
                ViewContext(null)
            }

            ViewPermission.GROUP -> {
                val ids = userRepository.findGroupMemberIds(requesterId).toSet()
                ViewContext(ids.ifEmpty { setOf(requesterId) })
            }

            ViewPermission.INDIVIDUAL -> {
                ViewContext(setOf(requesterId))
            }
        }
    }

    private fun requireUser(id: UUID): User =
        userRepository
            .findById(id)
            .orElseThrow { NoSuchElementException("User not found: $id") }
            .also { if (it.deletedAt != null) throw NoSuchElementException("User not found: $id") }

    private fun User.toSummary() =
        UserSummary(
            id = id!!,
            email = email,
            fullName = fullName,
            isActive = isActive,
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
