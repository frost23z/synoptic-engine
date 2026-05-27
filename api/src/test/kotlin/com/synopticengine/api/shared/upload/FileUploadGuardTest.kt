package com.synopticengine.api.shared.upload

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T4.3 — Pure unit tests for [FileUploadGuard].
 *
 * Covers:
 *  - Allowed MIME types pass through without exceptions.
 *  - Disallowed MIME types → [UnsupportedMediaTypeException] (→ HTTP 415).
 *  - Oversized files → [FileSizeLimitExceededException] (→ HTTP 400).
 *  - Per-endpoint limits: activity 10 MB, email attachment 25 MB, CSV import 10 MB.
 */
class FileUploadGuardTest {
    private val guard = FileUploadGuard()

    // ── Activity file: allowed types ──────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(
        strings = [
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain", "text/csv",
            "application/zip", "application/gzip",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ],
    )
    fun `validateActivityFile accepts allowed MIME types`(contentType: String) {
        val file = mockFile("doc.bin", contentType, 1024)
        // Should not throw
        guard.validateActivityFile(file)
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/x-sh", "text/html", "application/javascript", "application/x-executable"])
    fun `validateActivityFile rejects dangerous MIME types with 415`(contentType: String) {
        val file = mockFile("evil.sh", contentType, 1024)
        assertFailsWith<UnsupportedMediaTypeException> {
            guard.validateActivityFile(file)
        }
    }

    @Test
    fun `validateActivityFile rejects null content-type`() {
        val file = mockFile("doc.bin", null, 1024)
        assertFailsWith<UnsupportedMediaTypeException> {
            guard.validateActivityFile(file)
        }
    }

    @Test
    fun `validateActivityFile rejects file exceeding 10 MB`() {
        val file = mockFile("big.pdf", "application/pdf", FileUploadGuard.ACTIVITY_MAX_BYTES + 1)
        val ex =
            assertFailsWith<FileSizeLimitExceededException> {
                guard.validateActivityFile(file)
            }
        assertTrue(ex.message!!.contains("10 MB"), "Error message should mention the limit: ${ex.message}")
    }

    @Test
    fun `validateActivityFile accepts file at exactly 10 MB`() {
        val file = mockFile("exact.pdf", "application/pdf", FileUploadGuard.ACTIVITY_MAX_BYTES)
        guard.validateActivityFile(file) // must not throw
    }

    // ── Email attachment: 25 MB limit ─────────────────────────────────────────

    @Test
    fun `validateEmailAttachment accepts valid PDF under 25 MB`() {
        val file = mockFile("attach.pdf", "application/pdf", 1024)
        guard.validateEmailAttachment(file)
    }

    @Test
    fun `validateEmailAttachment rejects file exceeding 25 MB`() {
        val file = mockFile("huge.pdf", "application/pdf", FileUploadGuard.EMAIL_ATTACHMENT_MAX_BYTES + 1)
        assertFailsWith<FileSizeLimitExceededException> {
            guard.validateEmailAttachment(file)
        }
    }

    @Test
    fun `validateEmailAttachment accepts 10 MB file that would be rejected by activity limit`() {
        // A 15 MB file: passes email attachment (25 MB), would fail activity (10 MB)
        val fifteenMb = 15L * 1024 * 1024
        val file = mockFile("medium.zip", "application/zip", fifteenMb)
        guard.validateEmailAttachment(file) // must not throw
    }

    // ── CSV import: allowed types ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = ["text/csv", "text/plain", "application/vnd.ms-excel"])
    fun `validateCsvImport accepts CSV-compatible MIME types`(contentType: String) {
        val file = mockFile("import.csv", contentType, 1024)
        guard.validateCsvImport(file)
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/pdf", "image/png", "application/zip"])
    fun `validateCsvImport rejects non-CSV types`(contentType: String) {
        val file = mockFile("not-a-csv.pdf", contentType, 1024)
        assertFailsWith<UnsupportedMediaTypeException> {
            guard.validateCsvImport(file)
        }
    }

    @Test
    fun `validateCsvImport rejects CSV exceeding 10 MB`() {
        val file = mockFile("big.csv", "text/csv", FileUploadGuard.CSV_MAX_BYTES + 1)
        assertFailsWith<FileSizeLimitExceededException> {
            guard.validateCsvImport(file)
        }
    }

    // ── MIME type parsing ─────────────────────────────────────────────────────

    @Test
    fun `content-type with charset parameter is accepted`() {
        // Browsers sometimes send "text/plain; charset=UTF-8" for .csv files.
        val file = mockFile("data.csv", "text/plain; charset=UTF-8", 1024)
        guard.validateCsvImport(file) // must not throw
    }

    @Test
    fun `error message includes disallowed type`() {
        val file = mockFile("evil.exe", "application/x-msdownload", 1024)
        val ex =
            assertFailsWith<UnsupportedMediaTypeException> {
                guard.validateActivityFile(file)
            }
        assertTrue(
            ex.message!!.contains("application/x-msdownload"),
            "Error should mention the rejected type: ${ex.message}",
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mockFile(
        name: String,
        contentType: String?,
        sizeBytes: Long,
    ): MockMultipartFile {
        // MockMultipartFile stores content as a ByteArray; for size checks we only
        // need the byte count to match — we use a zero-filled array to avoid OOM
        // on very large sizes in the test. The guard checks file.size, which
        // MockMultipartFile.getSize() returns as the content array length.
        val content =
            if (sizeBytes <= 10_000_000L) {
                ByteArray(sizeBytes.toInt())
            } else {
                // For sizes > 10 MB we can't allocate full array — use a custom mock.
                return MockMultipartFileSized(name, contentType, sizeBytes)
            }
        return MockMultipartFile("file", name, contentType, content)
    }

    /**
     * Thin stand-in for oversized uploads: returns the configured size without
     * allocating the full byte array. The guard only calls [getSize] and
     * [getContentType], so this is sufficient.
     */
    private class MockMultipartFileSized(
        private val filename: String,
        private val mimeType: String?,
        private val sizeOverride: Long,
    ) : MockMultipartFile("file", filename, mimeType, ByteArray(0)) {
        override fun getSize(): Long = sizeOverride

        override fun getContentType(): String? = mimeType
    }
}
