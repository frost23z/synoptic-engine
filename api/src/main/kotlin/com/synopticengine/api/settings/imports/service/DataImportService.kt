package com.synopticengine.api.settings.imports.service

import com.synopticengine.api.settings.imports.domain.DataImport
import com.synopticengine.api.settings.imports.domain.ImportStatus
import com.synopticengine.api.settings.imports.repo.DataImportRepository
import com.synopticengine.api.settings.imports.web.DataImportResponse
import com.synopticengine.api.settings.imports.web.DataImportStatsResponse
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DataImportService(
    private val dataImportRepository: DataImportRepository,
    private val csvImportProcessor: CsvImportProcessor,
) {
    private val uploadDir: Path = Paths.get(System.getProperty("java.io.tmpdir"), "synoptic-imports")

    init {
        Files.createDirectories(uploadDir)
    }

    fun findAll(): List<DataImportResponse> = dataImportRepository.findAll().map { it.toResponse() }

    fun findById(id: UUID): DataImportResponse = requireImport(id).toResponse()

    fun getStats(id: UUID): DataImportStatsResponse {
        val imp = requireImport(id)
        return DataImportStatsResponse(
            id = imp.id!!,
            status = imp.status,
            errorCount = imp.errorCount,
            successCount = imp.successCount,
            errors = imp.errors,
        )
    }

    @Transactional
    fun upload(
        file: MultipartFile,
        entityType: String,
    ): DataImportResponse {
        // T2.5(a) — path-traversal protection on the original filename.
        // Normalise the filename: strip directory separators so a caller cannot
        // supply "../../etc/passwd" as the filename.  Then assert the resolved
        // path stays under uploadDir as a belt-and-braces check in addition to
        // the H13 hardening already applied in LocalStorageService.
        val rawName = file.originalFilename ?: "import.csv"
        val safeBasename =
            Paths
                .get(rawName)
                .fileName // drops any directory components
                ?.toString()
                ?.replace(Regex("[^a-zA-Z0-9._-]"), "_") // strip shell-unsafe chars
                ?.take(128) // cap length
                ?.ifBlank { "import.csv" }
                ?: "import.csv"

        val filename = "${UUID.randomUUID()}_$safeBasename"
        val filePath = uploadDir.resolve(filename).normalize()

        // Verify the resolved path is still under uploadDir (extra safety).
        check(filePath.startsWith(uploadDir.toRealPath())) {
            "Resolved upload path escapes upload directory: $filePath"
        }

        file.transferTo(filePath.toFile())

        val dataImport =
            dataImportRepository.save(
                DataImport().apply {
                    this.name = rawName.take(255)
                    this.filePath = filePath.toString()
                    this.entityType = entityType
                },
            )
        return dataImport.toResponse()
    }

    @Transactional
    fun startImport(id: UUID): DataImportResponse {
        val imp = requireImport(id)
        if (imp.status != ImportStatus.PENDING) {
            throw IllegalStateException("Import is not in PENDING state")
        }
        csvImportProcessor.process(imp.id!!)
        return imp.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val imp = requireImport(id)
        Files.deleteIfExists(Paths.get(imp.filePath))
        dataImportRepository.delete(imp)
    }

    @Transactional
    fun validate(id: UUID): DataImportResponse {
        val imp = requireImport(id)
        if (imp.status != ImportStatus.PENDING) throw IllegalStateException("Import must be PENDING to validate")
        val importId = checkNotNull(imp.id) { "Import id must not be null for validation" }
        csvImportProcessor.validate(importId)
        return requireImport(id).toResponse()
    }

    @Transactional
    fun link(id: UUID): DataImportResponse {
        val imp = requireImport(id)
        if (imp.status != ImportStatus.PENDING) {
            throw IllegalStateException("Import must be PENDING to link")
        }
        val importId = checkNotNull(imp.id) { "Import id must not be null for link" }
        csvImportProcessor.validate(importId)
        val refreshed = requireImport(id)
        if (refreshed.errorCount > 0) {
            throw IllegalStateException("Import has validation errors and cannot be linked")
        }
        return refreshed.toResponse()
    }

    @Transactional
    fun indexData(id: UUID): DataImportResponse {
        val imp = requireImport(id)
        if (imp.status != ImportStatus.PENDING) throw IllegalStateException("Import must be PENDING to process")
        val importId = checkNotNull(imp.id) { "Import id must not be null for processing" }
        csvImportProcessor.process(importId)
        return requireImport(id).toResponse()
    }

    fun downloadErrorsCsv(id: UUID): String {
        val imp = requireImport(id)
        val writer = StringWriter()
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT
                .builder()
                .setHeader("row", "error")
                .build(),
        ).use { printer ->
            imp.errors?.forEach { error ->
                printer.printRecord(error["row"], error["error"])
            }
        }
        return writer.toString()
    }

    // Tenant-aware load. See EmailService.requireEmail for the IDOR rationale.
    private fun requireImport(id: UUID): DataImport =
        dataImportRepository.findActiveById(id) ?: throw NoSuchElementException("Import not found: $id")
}

fun DataImport.toResponse() =
    DataImportResponse(
        id = id!!,
        name = name,
        entityType = entityType,
        status = status,
        errorCount = errorCount,
        successCount = successCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
