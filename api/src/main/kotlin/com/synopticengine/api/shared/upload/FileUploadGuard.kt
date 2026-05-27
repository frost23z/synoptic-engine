package com.synopticengine.api.shared.upload

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

/**
 * T4.3 — File-upload hardening.
 *
 * Validates incoming [MultipartFile] uploads against:
 *  1. Maximum file size (configurable per upload type).
 *  2. MIME-type allow-list (configurable per upload type).
 *
 * Callers that detect an invalid file should throw [UnsupportedMediaTypeException]
 * (→ HTTP 415) for unknown types and [FileSizeLimitExceededException] (→ HTTP 400)
 * for oversized uploads.
 *
 * The guard is intentionally **not** a Spring interceptor so callers retain full
 * control over which endpoints are validated and what limits apply.
 */
@Component
class FileUploadGuard {
    /**
     * Validate an activity-file attachment.
     *
     * Allowed types: common document + image + archive formats.
     * Max size: 10 MB.
     */
    fun validateActivityFile(file: MultipartFile) = validate(file, ACTIVITY_MAX_BYTES, ALLOWED_DOCUMENT_TYPES)

    /**
     * Validate an email attachment (inline upload during compose).
     *
     * Allowed types: common document + image + archive formats.
     * Max size: 25 MB.
     */
    fun validateEmailAttachment(file: MultipartFile) =
        validate(file, EMAIL_ATTACHMENT_MAX_BYTES, ALLOWED_DOCUMENT_TYPES)

    /**
     * Validate a CSV data-import file.
     *
     * Allowed types: text/csv and the legacy Excel MIME type.
     * Max size: 10 MB.
     */
    fun validateCsvImport(file: MultipartFile) = validate(file, CSV_MAX_BYTES, ALLOWED_CSV_TYPES)

    // ── private ───────────────────────────────────────────────────────────────

    private fun validate(
        file: MultipartFile,
        maxBytes: Long,
        allowedMediaTypes: Set<String>,
    ) {
        // 1. Size check
        if (file.size > maxBytes) {
            throw FileSizeLimitExceededException(
                "File '${file.originalFilename}' exceeds the maximum allowed size of " +
                    "${maxBytes / (1024 * 1024)} MB (uploaded: ${file.size / (1024 * 1024)} MB)",
            )
        }

        // 2. MIME-type check (from the Content-Type header supplied by the client)
        val declared = file.contentType?.let { parseBaseType(it) }
        if (declared == null || declared !in allowedMediaTypes) {
            throw UnsupportedMediaTypeException(
                "File type '${declared ?: "unknown"}' is not allowed. " +
                    "Permitted types: ${allowedMediaTypes.joinToString()}",
            )
        }
    }

    /** Strip parameters (e.g. `text/plain; charset=UTF-8` → `text/plain`). */
    private fun parseBaseType(contentType: String): String =
        try {
            MediaType.parseMediaType(contentType).let { "${it.type}/${it.subtype}" }
        } catch (_: Exception) {
            contentType.substringBefore(";").trim().lowercase()
        }

    companion object {
        /** 10 MB in bytes. */
        const val ACTIVITY_MAX_BYTES: Long = 10L * 1024 * 1024

        /** 25 MB in bytes. */
        const val EMAIL_ATTACHMENT_MAX_BYTES: Long = 25L * 1024 * 1024

        /** 10 MB in bytes. */
        const val CSV_MAX_BYTES: Long = 10L * 1024 * 1024

        /**
         * Allow-list of MIME types accepted for general document/image uploads.
         *
         * This set is deliberately conservative. Expand with care; executable
         * or scripting types must never be added.
         */
        val ALLOWED_DOCUMENT_TYPES: Set<String> =
            setOf(
                // Images
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/webp",
                "image/svg+xml",
                // Documents
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                // Plain text
                "text/plain",
                "text/csv",
                // Archives
                "application/zip",
                "application/x-zip-compressed",
                "application/gzip",
            )

        /**
         * Allow-list of MIME types accepted for CSV data-import uploads.
         * Intentionally narrower than [ALLOWED_DOCUMENT_TYPES].
         */
        val ALLOWED_CSV_TYPES: Set<String> =
            setOf(
                "text/csv",
                "text/plain", // some browsers send text/plain for .csv files
                "application/vnd.ms-excel", // legacy Excel, sometimes used for .csv
            )
    }
}

/** Thrown when an uploaded file exceeds the maximum allowed size. Mapped to HTTP 400. */
class FileSizeLimitExceededException(
    message: String,
) : IllegalArgumentException(message)

/** Thrown when an uploaded file has an unsupported MIME type. Mapped to HTTP 415. */
class UnsupportedMediaTypeException(
    message: String,
) : RuntimeException(message)
