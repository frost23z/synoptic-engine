package com.synopticengine.api.crypto

import com.synopticengine.api.shared.crypto.AesGcmEncryptionConverter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T2.4 — Unit tests for [AesGcmEncryptionConverter] (encrypt/decrypt round-trip).
 *
 * Pure unit tests: no Spring context, no Testcontainers.
 * Run via `./gradlew unitTests`.
 */
class AesGcmEncryptionConverterTest {
    private lateinit var converter: AesGcmEncryptionConverter

    @BeforeEach
    fun setUp() {
        // Use the same 32-byte test key as application-test.yaml.
        AesGcmEncryptionConverter.initKey(TEST_KEY_BASE64)
        converter = AesGcmEncryptionConverter()
    }

    @Test
    fun `null attribute converts to null database column`() {
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun `null database column converts to null attribute`() {
        assertNull(converter.convertToEntityAttribute(null))
    }

    @Test
    fun `plaintext survives encrypt then decrypt unchanged`() {
        val original = "super-secret-webhook-hmac-key-12345"
        val encrypted = converter.convertToDatabaseColumn(original)!!
        val decrypted = converter.convertToEntityAttribute(encrypted)
        assertEquals(original, decrypted, "Decrypted value must equal the original plaintext")
    }

    @Test
    fun `encrypted value starts with ENC prefix`() {
        val encrypted = converter.convertToDatabaseColumn("my-secret")!!
        assertTrue(
            encrypted.startsWith("ENC:"),
            "Encrypted DB column value must start with 'ENC:' but was: $encrypted",
        )
    }

    @Test
    fun `two encryptions of the same value produce different ciphertexts due to random IV`() {
        val plaintext = "repeat-me"
        val enc1 = converter.convertToDatabaseColumn(plaintext)
        val enc2 = converter.convertToDatabaseColumn(plaintext)
        assertNotEquals(
            enc1,
            enc2,
            "Each encryption must use a fresh random IV, producing unique ciphertexts",
        )
    }

    @Test
    fun `plaintext without ENC prefix is returned as-is during transition period`() {
        // Simulates a pre-migration row that was stored in plaintext.
        val plaintext = "old-plaintext-secret"
        val readBack = converter.convertToEntityAttribute(plaintext)
        assertEquals(
            plaintext,
            readBack,
            "Plaintext rows (no ENC prefix) must be returned unchanged for backward compatibility",
        )
    }

    @Test
    fun `encrypted value is not readable as plaintext`() {
        val original = "sensitive-value"
        val encrypted = converter.convertToDatabaseColumn(original)!!
        assertFalse(encrypted.contains(original), "The raw DB value must not contain the plaintext")
    }

    @Test
    fun `empty string round-trips correctly`() {
        val encrypted = converter.convertToDatabaseColumn("")!!
        val decrypted = converter.convertToEntityAttribute(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun `unicode string round-trips correctly`() {
        val original = "Héllo Wörld 🔑"
        val encrypted = converter.convertToDatabaseColumn(original)!!
        val decrypted = converter.convertToEntityAttribute(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `no-key mode returns plaintext passthrough on write`() {
        AesGcmEncryptionConverter.initKey(null) // clear key → passthrough mode
        try {
            val converter2 = AesGcmEncryptionConverter()
            val result = converter2.convertToDatabaseColumn("my-value")
            // Without a key, the converter stores plaintext (no ENC prefix).
            assertEquals(
                "my-value",
                result,
                "Without a key, convertToDatabaseColumn must return the plaintext unchanged",
            )
        } finally {
            // Restore key for subsequent tests.
            AesGcmEncryptionConverter.initKey(TEST_KEY_BASE64)
        }
    }

    companion object {
        /** Same 32-byte test key as application-test.yaml. */
        private const val TEST_KEY_BASE64 = "U1lOT1BUSUNfVEVTVF9FTkNSWVBUSU9OX0tFWTMyQiE="
    }
}
