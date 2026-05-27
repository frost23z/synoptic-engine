package com.synopticengine.api.shared.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JPA [AttributeConverter] that transparently encrypts/decrypts string column values
 * using AES-256-GCM (authenticated encryption). Applied to:
 *  - `webhooks.secret`       (always a secret; T2.4)
 *  - `system_configs.value`  (all values; the `is_secret` flag controls response masking)
 *
 * **Key supply.** The 32-byte (256-bit) key is base64-encoded and supplied through
 * the environment variable `SYNOPTIC_ENCRYPTION_KEY`. The [EncryptionKeyInitializer]
 * Spring component reads the key at startup and passes it to [initKey]. On non-dev
 * profiles, [SecretsGuard] refuses to start if the key is absent.
 *
 * **Wire format.** Encrypted values are stored as:
 *
 *   `ENC:<base64(12-byte IV || ciphertext || 16-byte GCM auth-tag)>`
 *
 * The `ENC:` prefix distinguishes encrypted rows from plaintext rows that existed
 * before this migration. On read:
 *  - Values that start with `ENC:` are decrypted.
 *  - Values that do NOT start with `ENC:` are returned as-is (plaintext transition
 *    rows). Re-saving the entity encrypts them on the next write.
 *
 * **Rollout.** When first deploying with encryption enabled, existing plaintext rows
 * are read transparently. They are re-encrypted lazily on the next UPDATE. For a
 * one-shot re-encryption, run:
 *
 *   ```sql
 *   -- Force a touch on all webhook secrets (triggers JPA write via the converter):
 *   -- Use the application's admin API or a script that loads and re-saves each entity.
 *   ```
 *
 *   A bulk SQL re-encryption is possible but requires the key to be available inside
 *   the database (e.g. via a pgcrypto extension), which adds operational complexity.
 *   Lazy re-encryption is simpler and safe because the plaintext rows are protected
 *   by transport-layer security and OS-level access controls until re-written.
 *
 * **Dev/test.** In `local`, `test`, `dev` profiles, [EncryptionKeyInitializer] uses a
 * fixed 32-byte dev key if `SYNOPTIC_ENCRYPTION_KEY` is absent, so tests work without
 * environment setup. Startup logs a clear WARNING.
 */
@Converter
class AesGcmEncryptionConverter : AttributeConverter<String?, String?> {
    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val key = secretKey ?: return attribute // no key → store plaintext (dev/test without key)
        return ENCRYPTED_PREFIX + encrypt(key, attribute)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        if (!dbData.startsWith(ENCRYPTED_PREFIX)) return dbData // plaintext transition row
        // no key → return base64 blob as-is (won't be meaningful but won't crash)
        val key = secretKey ?: return dbData.removePrefix(ENCRYPTED_PREFIX)
        return decrypt(key, dbData.removePrefix(ENCRYPTED_PREFIX))
    }

    private fun encrypt(
        key: SecretKey,
        plaintext: String,
    ): String {
        val iv = ByteArray(GCM_IV_LENGTH)
        SECURE_RANDOM.nextBytes(iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext // 12 bytes IV + ciphertext+tag
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(
        key: SecretKey,
        base64: String,
    ): String {
        val combined = Base64.getDecoder().decode(base64)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12 // 96-bit IV recommended for GCM
        private const val GCM_TAG_BITS = 128 // 128-bit auth tag
        internal const val ENCRYPTED_PREFIX = "ENC:"

        private val SECURE_RANDOM = SecureRandom()
        private val log = LoggerFactory.getLogger(AesGcmEncryptionConverter::class.java)

        @Volatile
        @JvmField
        internal var secretKey: SecretKey? = null

        /**
         * Called by [EncryptionKeyInitializer] at Spring startup. Pass `null` to
         * operate in plaintext-passthrough mode (dev without a key configured).
         */
        fun initKey(base64Key: String?) {
            if (base64Key.isNullOrBlank()) {
                log.warn(
                    "SYNOPTIC_ENCRYPTION_KEY is not set. Encrypted columns (webhooks.secret, " +
                        "system_configs.value) will be stored in PLAINTEXT. " +
                        "Set the key before deploying to production.",
                )
                secretKey = null
                return
            }
            val keyBytes = Base64.getDecoder().decode(base64Key)
            require(keyBytes.size == 32) {
                "SYNOPTIC_ENCRYPTION_KEY must be a base64-encoded 32-byte (256-bit) key; " +
                    "got ${keyBytes.size} bytes"
            }
            secretKey = SecretKeySpec(keyBytes, "AES")
            log.info("AES-GCM encryption key loaded for at-rest secret columns.")
        }
    }
}
