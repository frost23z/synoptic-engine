package com.synopticengine.api.settings.imports.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.inventory.InventoryApi
import com.synopticengine.api.settings.imports.domain.DataImport
import com.synopticengine.api.settings.imports.domain.ImportStatus
import com.synopticengine.api.settings.imports.repo.DataImportRepository
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.UUID

@Component
class CsvImportProcessor(
    private val dataImportRepository: DataImportRepository,
    private val crmApi: CrmApi,
    private val inventoryApi: InventoryApi,
) {
    private val log = LoggerFactory.getLogger(CsvImportProcessor::class.java)

    @Async
    fun process(importId: UUID) {
        val dataImport = dataImportRepository.findById(importId).orElse(null) ?: return
        dataImport.status = ImportStatus.PROCESSING
        dataImportRepository.save(dataImport)

        try {
            when (dataImport.entityType.lowercase()) {
                "person" -> {
                    processPersons(dataImport)
                }

                "lead" -> {
                    processLeads(dataImport)
                }

                "product" -> {
                    processProducts(dataImport)
                }

                else -> {
                    dataImport.status = ImportStatus.FAILED
                    dataImport.errors =
                        listOf(mapOf("row" to "0", "error" to "Unknown entity type: ${dataImport.entityType}"))
                    dataImportRepository.save(dataImport)
                    return
                }
            }
        } catch (e: Exception) {
            log.error("Import $importId failed: ${e.message}")
            dataImport.status = ImportStatus.FAILED
            dataImport.errors = listOf(mapOf("row" to "0", "error" to (e.message ?: "Unknown error")))
            dataImportRepository.save(dataImport)
        }
    }

    private fun processPersons(dataImport: DataImport) {
        val errors = mutableListOf<Map<String, String>>()
        var successCount = 0
        var rowIndex = 1

        parseCsv(dataImport.filePath).use { parser ->
            for (record in parser) {
                rowIndex++
                try {
                    val firstName = record.get("firstName").ifBlank { null }
                    val lastName = record.get("lastName").ifBlank { null }
                    if (firstName == null || lastName == null) {
                        errors.add(
                            mapOf("row" to rowIndex.toString(), "error" to "firstName and lastName are required"),
                        )
                        continue
                    }
                    crmApi.createPerson(
                        firstName = firstName,
                        lastName = lastName,
                        email = record.get("email").ifBlank { null },
                        phone = record.get("phone").ifBlank { null },
                        jobTitle = record.get("jobTitle").ifBlank { null },
                    )
                    successCount++
                } catch (e: Exception) {
                    errors.add(mapOf("row" to rowIndex.toString(), "error" to (e.message ?: "Unknown error")))
                }
            }
        }

        dataImport.successCount = successCount
        dataImport.errorCount = errors.size
        dataImport.errors = errors.ifEmpty { null }
        dataImport.status = ImportStatus.COMPLETED
        dataImportRepository.save(dataImport)
    }

    private fun processLeads(dataImport: DataImport) {
        val errors = mutableListOf<Map<String, String>>()
        var successCount = 0
        var rowIndex = 1

        parseCsv(dataImport.filePath).use { parser ->
            for (record in parser) {
                rowIndex++
                try {
                    val title = record.get("title").ifBlank { null }
                    if (title == null) {
                        errors.add(mapOf("row" to rowIndex.toString(), "error" to "title is required"))
                        continue
                    }
                    val pipelineId =
                        record
                            .get("pipelineId")
                            .ifBlank { null }
                            ?.let { UUID.fromString(it) }
                            ?: UUID.fromString("00000000-0000-0000-0000-000000000010")
                    val stageId =
                        record
                            .get("stageId")
                            .ifBlank { null }
                            ?.let { UUID.fromString(it) }
                            ?: UUID.fromString("00000000-0000-0000-0000-000000000011")
                    val amount = record.get("amount").ifBlank { null }?.let { BigDecimal(it) }

                    crmApi.createLead(
                        title = title,
                        description = record.get("description").ifBlank { null },
                        amount = amount,
                        pipelineId = pipelineId,
                        stageId = stageId,
                    )
                    successCount++
                } catch (e: Exception) {
                    errors.add(mapOf("row" to rowIndex.toString(), "error" to (e.message ?: "Unknown error")))
                }
            }
        }

        dataImport.successCount = successCount
        dataImport.errorCount = errors.size
        dataImport.errors = errors.ifEmpty { null }
        dataImport.status = ImportStatus.COMPLETED
        dataImportRepository.save(dataImport)
    }

    private fun processProducts(dataImport: DataImport) {
        val errors = mutableListOf<Map<String, String>>()
        var successCount = 0
        var rowIndex = 1

        parseCsv(dataImport.filePath).use { parser ->
            for (record in parser) {
                rowIndex++
                try {
                    val name = record.get("name").ifBlank { null }
                    if (name == null) {
                        errors.add(mapOf("row" to rowIndex.toString(), "error" to "name is required"))
                        continue
                    }
                    val price = record.get("price").ifBlank { "0" }.let { BigDecimal(it) }
                    inventoryApi.createProduct(
                        name = name,
                        description = record.get("description").ifBlank { null },
                        price = price,
                        sku = record.get("sku").ifBlank { null },
                    )
                    successCount++
                } catch (e: Exception) {
                    errors.add(mapOf("row" to rowIndex.toString(), "error" to (e.message ?: "Unknown error")))
                }
            }
        }

        dataImport.successCount = successCount
        dataImport.errorCount = errors.size
        dataImport.errors = errors.ifEmpty { null }
        dataImport.status = ImportStatus.COMPLETED
        dataImportRepository.save(dataImport)
    }

    fun validate(importId: UUID) {
        val dataImport = dataImportRepository.findById(importId).orElse(null) ?: return
        val errors = mutableListOf<Map<String, String>>()
        var rowIndex = 1
        try {
            parseCsv(dataImport.filePath).use { parser ->
                for (record in parser) {
                    rowIndex++
                    val headers = record.toMap().keys
                    val hasRequiredField =
                        when (dataImport.entityType.lowercase()) {
                            "person" -> "first_name" in headers || "firstName" in headers
                            "lead" -> "title" in headers
                            "product" -> "name" in headers
                            else -> true
                        }
                    if (!hasRequiredField) {
                        errors.add(mapOf("row" to rowIndex.toString(), "error" to "Missing required column"))
                    }
                }
            }
        } catch (e: Exception) {
            errors.add(mapOf("row" to "0", "error" to "File parse error: ${e.message}"))
        }
        dataImport.errors = if (errors.isEmpty()) dataImport.errors else errors
        dataImport.errorCount = errors.size
        dataImportRepository.save(dataImport)
    }

    private fun parseCsv(filePath: String): CSVParser =
        CSVParser.parse(
            File(filePath),
            StandardCharsets.UTF_8,
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build(),
        )
}
