package com.synopticengine.api.shared.crypto

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Reads `SYNOPTIC_ENCRYPTION_KEY` at startup and initialises [AesGcmEncryptionConverter].
 *
 * JPA `@Converter` classes are instantiated by Hibernate, not by Spring, so they cannot
 * receive `@Autowired` dependencies. This component bridges the gap: it runs at Spring
 * startup (via `@PostConstruct`) and writes the key into the converter's companion-object
 * field, which is then available to all converter instances created by Hibernate.
 *
 * The key must be a **base64-encoded 32-byte (256-bit) secret**.  Generate one with:
 *
 *   ```shell
 *   openssl rand -base64 32
 *   ```
 *
 * **Fail-closed policy.** [SecretsGuard] (T1.4) refuses to start on non-dev profiles
 * when the key is absent. This bean only supplies the key; the guard enforces the
 * policy. On dev profiles a null key is allowed — the converter falls back to plaintext
 * passthrough and logs a WARNING.
 */
@Component
class EncryptionKeyInitializer(
    @Value("\${synoptic.encryption.key:}") private val encryptionKey: String,
) {
    @PostConstruct
    fun init() {
        AesGcmEncryptionConverter.initKey(encryptionKey.ifBlank { null })
    }
}
