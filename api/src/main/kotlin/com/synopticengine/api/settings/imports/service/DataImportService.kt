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

    fun findById(id: UUID): DataImportResponse =
        dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }.toResponse()

    fun getStats(id: UUID): DataImportStatsResponse {
        val imp = dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }
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
        val filename = "${UUID.randomUUID()}_${file.originalFilename ?: "import.csv"}"
        val filePath = uploadDir.resolve(filename)
        file.transferTo(filePath.toFile())

        val dataImport =
            dataImportRepository.save(
                DataImport().apply {
                    this.name = file.originalFilename ?: "import.csv"
                    this.filePath = filePath.toString()
                    this.entityType = entityType
                },
            )
        return dataImport.toResponse()
    }

    @Transactional
    fun startImport(id: UUID): DataImportResponse {
        val imp = dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }
        if (imp.status != ImportStatus.PENDING) {
            throw IllegalStateException("Import is not in PENDING state")
        }
        csvImportProcessor.process(imp.id!!)
        return imp.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val imp = dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }
        Files.deleteIfExists(Paths.get(imp.filePath))
        dataImportRepository.deleteById(id)
    }

    @Transactional
    fun validate(id: UUID): DataImportResponse {
        val imp = dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }
        if (imp.status != ImportStatus.PENDING) throw IllegalStateException("Import must be PENDING to validate")
        csvImportProcessor.validate(imp.id!!)
        return dataImportRepository.findById(id).get().toResponse()
    }

    @Transactional
    fun link(id: UUID): DataImportResponse {
        val imp = dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }
        return imp.toResponse()
    }

    @Transactional
    fun indexData(id: UUID): DataImportResponse {
        val imp = dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }
        if (imp.status != ImportStatus.PENDING) throw IllegalStateException("Import must be PENDING to process")
        csvImportProcessor.process(imp.id!!)
        return dataImportRepository.findById(id).get().toResponse()
    }

    fun downloadErrorsCsv(id: UUID): String {
        val imp = dataImportRepository.findById(id).orElseThrow { NoSuchElementException("Import not found: $id") }
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
