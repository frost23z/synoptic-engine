package com.synopticengine.api.auth.service

import com.synopticengine.api.auth.domain.MfaBackupCode
import com.synopticengine.api.auth.domain.MfaConfig
import com.synopticengine.api.auth.repo.MfaBackupCodeRepository
import com.synopticengine.api.auth.repo.MfaConfigRepository
import com.synopticengine.api.auth.web.MfaSetupResponse
import com.synopticengine.api.identity.IdentityApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MfaService(
    private val mfaConfigRepository: MfaConfigRepository,
    private val mfaBackupCodeRepository: MfaBackupCodeRepository,
    private val totpService: TotpService,
    private val identityApi: IdentityApi,
) {
    fun isEnabled(userId: UUID): Boolean = mfaConfigRepository.findActiveByUserId(userId)?.enabled == true

    /**
     * Generates a new TOTP secret and 8 one-time backup codes.
     * MFA is NOT yet enabled — caller must call [confirm] with a valid TOTP code.
     * Any previously pending (unconfirmed) setup is replaced.
     */
    @Transactional
    fun setup(userId: UUID): MfaSetupResponse {
        val email = identityApi.findById(userId)?.email ?: throw NoSuchElementException("User not found: $userId")
        val existing = mfaConfigRepository.findActiveByUserId(userId)
        if (existing != null) {
            if (existing.enabled) throw IllegalStateException("MFA is already enabled. Disable it first.")
            existing.deletedAt = Instant.now()
            mfaConfigRepository.save(existing)
        }
        val secret = totpService.generateSecret()
        mfaConfigRepository.save(
            MfaConfig().apply {
                this.userId = userId
                this.totpSecret = secret
                this.enabled = false
            },
        )
        return MfaSetupResponse(
            secret = secret,
            qrUri = totpService.buildQrUri(secret, email),
        )
    }

    /**
     * Confirms setup: validates the TOTP [code] against the pending secret and enables MFA.
     * Also generates and persists 8 backup codes (returned once only).
     */
    @Transactional
    fun confirm(
        userId: UUID,
        code: String,
    ): List<String> {
        val config =
            mfaConfigRepository.findActiveByUserId(userId)
                ?: throw IllegalStateException("No pending MFA setup found. Call setup first.")
        if (config.enabled) throw IllegalStateException("MFA is already confirmed.")
        if (!totpService.verify(config.totpSecret, code)) throw IllegalArgumentException("Invalid TOTP code")
        config.enabled = true
        mfaConfigRepository.save(config)
        return replaceBackupCodes(userId)
    }

    /**
     * Verifies [code] as either a TOTP code or a backup code.
     * Returns true on success; consumes the backup code if one is used.
     */
    @Transactional
    fun verify(
        userId: UUID,
        code: String,
    ): Boolean {
        val config = mfaConfigRepository.findActiveByUserId(userId) ?: return false
        if (!config.enabled) return false
        if (totpService.verify(config.totpSecret, code)) return true
        return tryConsumeBackupCode(userId, code)
    }

    /**
     * Disables MFA after verifying [code] (TOTP or backup code).
     * Clears all backup codes and soft-deletes the config.
     */
    @Transactional
    fun disable(
        userId: UUID,
        code: String,
    ) {
        val config =
            mfaConfigRepository.findActiveByUserId(userId)
                ?: throw IllegalStateException("MFA is not enabled.")
        if (!verify(userId, code)) throw IllegalArgumentException("Invalid TOTP or backup code")
        mfaBackupCodeRepository.deleteAllByUserId(userId)
        config.deletedAt = Instant.now()
        config.enabled = false
        mfaConfigRepository.save(config)
    }

    /**
     * Regenerates backup codes. Requires a valid TOTP [code] to prevent abuse.
     * Returns the new plaintext codes (shown once only).
     */
    @Transactional
    fun regenerateBackupCodes(
        userId: UUID,
        code: String,
    ): List<String> {
        val config =
            mfaConfigRepository.findActiveByUserId(userId)
                ?: throw IllegalStateException("MFA is not enabled.")
        if (!config.enabled) throw IllegalStateException("MFA is not enabled.")
        if (!totpService.verify(config.totpSecret, code)) throw IllegalArgumentException("Invalid TOTP code")
        return replaceBackupCodes(userId)
    }

    private fun replaceBackupCodes(userId: UUID): List<String> {
        mfaBackupCodeRepository.deleteAllByUserId(userId)
        val plaintexts = (1..BACKUP_CODE_COUNT).map { generateBackupCode() }
        plaintexts.forEach { plain ->
            mfaBackupCodeRepository.save(
                MfaBackupCode().apply {
                    this.userId = userId
                    this.codeHash = hashCode(plain)
                },
            )
        }
        return plaintexts
    }

    private fun tryConsumeBackupCode(
        userId: UUID,
        code: String,
    ): Boolean {
        val hash = hashCode(code)
        val match = mfaBackupCodeRepository.findUnusedByUserId(userId).firstOrNull { it.codeHash == hash } ?: return false
        match.usedAt = Instant.now()
        mfaBackupCodeRepository.save(match)
        return true
    }

    private fun generateBackupCode(): String {
        val bytes = ByteArray(6)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashCode(code: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(code.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val BACKUP_CODE_COUNT = 8
        private val secureRandom = SecureRandom()
    }
}
