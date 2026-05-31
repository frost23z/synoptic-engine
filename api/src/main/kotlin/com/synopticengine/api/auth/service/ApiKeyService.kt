package com.synopticengine.api.auth.service

import com.synopticengine.api.auth.UserPrincipal
import com.synopticengine.api.auth.domain.ApiKey
import com.synopticengine.api.auth.repo.ApiKeyRepository
import com.synopticengine.api.auth.web.ApiKeyCreateResponse
import com.synopticengine.api.auth.web.ApiKeyResponse
import com.synopticengine.api.identity.IdentityApi
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val identityApi: IdentityApi,
) {
    @Transactional
    fun create(
        tenantId: UUID,
        userId: UUID,
        name: String,
        expiresAt: Instant?,
    ): ApiKeyCreateResponse {
        val rawKey = generateKey()
        val prefix = rawKey.take(12)
        val hash = hashKey(rawKey)
        val saved =
            apiKeyRepository.save(
                ApiKey().apply {
                    this.tenantId = tenantId
                    this.userId = userId
                    this.name = name
                    this.keyHash = hash
                    this.keyPrefix = prefix
                    this.expiresAt = expiresAt
                },
            )
        return ApiKeyCreateResponse(
            id = saved.id,
            name = saved.name,
            key = rawKey,
            prefix = prefix,
            createdAt = saved.createdAt,
            expiresAt = saved.expiresAt,
        )
    }

    fun list(
        tenantId: UUID,
        userId: UUID,
    ): List<ApiKeyResponse> = apiKeyRepository.findActiveByTenantAndUser(tenantId, userId).map { it.toResponse() }

    @Transactional
    fun revoke(
        tenantId: UUID,
        userId: UUID,
        keyId: UUID,
    ) {
        val key =
            apiKeyRepository.findById(keyId).orElse(null)
                ?: throw NoSuchElementException("API key not found")
        if (key.tenantId != tenantId || key.userId != userId) throw NoSuchElementException("API key not found")
        if (key.revokedAt != null) return
        key.revokedAt = Instant.now()
        apiKeyRepository.save(key)
    }

    /**
     * Called from JwtAuthFilter when the bearer token starts with "sk_".
     * Runs without TenantContext — the filter sets it after this call returns.
     * Updates last_used_at on each successful auth.
     */
    @Transactional
    fun authenticateByKey(rawKey: String): UserPrincipal? {
        val now = Instant.now()
        val key = apiKeyRepository.findActiveByHash(hashKey(rawKey), now) ?: return null
        val credentials = identityApi.findCredentialsById(key.userId) ?: return null
        if (!credentials.isActive || credentials.deletedAt != null) return null
        key.lastUsedAt = now
        apiKeyRepository.save(key)
        return UserPrincipal(
            id = credentials.id,
            tenantId = credentials.tenantId,
            email = credentials.email,
            authorities = credentials.authorities.map { SimpleGrantedAuthority(it) }.toSet(),
        )
    }

    private fun generateKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashKey(key: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private val secureRandom = SecureRandom()
    }
}

fun ApiKey.toResponse() =
    ApiKeyResponse(
        id = id,
        name = name,
        prefix = keyPrefix,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastUsedAt = lastUsedAt,
    )
