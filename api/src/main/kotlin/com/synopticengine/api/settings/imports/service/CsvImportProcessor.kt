package com.synopticengine.api.settings.imports.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.inventory.InventoryApi
import com.synopticengine.api.settings.imports.domain.DataImport
import com.synopticengine.api.settings.imports.domain.ImportStatus
import com.synopticengine.api.settings.imports.repo.DataImportRepository
import com.synopticengine.api.shared.TenantContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
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
    @Transactional
    fun process(importId: UUID) {
        // P1-1: this is @Async; submission happens from DataImportController inside an
        // authenticated request, and TenantPropagatingTaskDecorator carries the tenant
        // across the boundary. Fail loudly if a future caller submits without one
        // rather than create cross-tenant entities by accident.
        TenantContext.get()
            ?: error("CsvImportProcessor.process called without an active TenantContext (importId=$importId)")

        // Use the tenant-aware finder (JPQL) so this async/scheduled path can't
        // pick up an import row that belongs to a different tenant — relevant if
        // an attacker ever managed to enqueue a foreign import id.
        val dataImport = dataImportRepository.findActiveById(importId) ?: return
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
                    throw IllegalArgumentException("Unknown entity type: ${dataImport.entityType}")
                }
            }
        } catch (e: Exception) {
            log.error("Import $importId failed: ${e.message}")
            markFailed(importId, listOf(mapOf("row" to "0", "error" to (e.message ?: "Unknown error"))))
            throw e
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

        if (errors.isNotEmpty()) {
            throw IllegalStateException("CSV import aborted: ${errors.size} row(s) failed validation")
        }
        dataImport.successCount = successCount
        dataImport.errorCount = 0
        dataImport.errors = null
        dataImport.status = ImportStatus.COMPLETED
        dataImportRepository.save(dataImport)
    }

    private fun processLeads(dataImport: DataImport) {
        val errors = mutableListOf<Map<String, String>>()
        var successCount = 0
        var rowIndex = 1
        val defaultRouting = crmApi.findDefaultLeadRouting()

        parseCsv(dataImport.filePath).use { parser ->
            for (record in parser) {
                rowIndex++
                try {
                    val title = record.get("title").ifBlank { null }
                    if (title == null) {
                        errors.add(mapOf("row" to rowIndex.toString(), "error" to "title is required"))
                        continue
                    }
                    val pipelineIdRaw = record.get("pipelineId").ifBlank { null }
                    val stageIdRaw = record.get("stageId").ifBlank { null }
                    val pipelineId = pipelineIdRaw?.let { UUID.fromString(it) }
                    val stageId = stageIdRaw?.let { UUID.fromString(it) }
                    val resolvedRouting =
                        when {
                            pipelineId != null && stageId != null -> {
                                pipelineId to stageId
                            }

                            pipelineId == null && stageId == null && defaultRouting != null -> {
                                defaultRouting.pipelineId to defaultRouting.stageId
                            }

                            pipelineId == null && stageId == null -> {
                                throw IllegalArgumentException(
                                    "pipelineId and stageId are required when no default pipeline/stage is configured",
                                )
                            }

                            else -> {
                                throw IllegalArgumentException(
                                    "pipelineId and stageId must either both be provided or both omitted",
                                )
                            }
                        }
                    val amount = record.get("amount").ifBlank { null }?.let { BigDecimal(it) }

                    crmApi.createLead(
                        title = title,
                        description = record.get("description").ifBlank { null },
                        amount = amount,
                        pipelineId = resolvedRouting.first,
                        stageId = resolvedRouting.second,
                    )
                    successCount++
                } catch (e: Exception) {
                    errors.add(mapOf("row" to rowIndex.toString(), "error" to (e.message ?: "Unknown error")))
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw IllegalStateException("CSV import aborted: ${errors.size} row(s) failed validation")
        }
        dataImport.successCount = successCount
        dataImport.errorCount = 0
        dataImport.errors = null
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

        if (errors.isNotEmpty()) {
            throw IllegalStateException("CSV import aborted: ${errors.size} row(s) failed validation")
        }
        dataImport.successCount = successCount
        dataImport.errorCount = 0
        dataImport.errors = null
        dataImport.status = ImportStatus.COMPLETED
        dataImportRepository.save(dataImport)
    }

    fun validate(importId: UUID) {
        // Use the tenant-aware finder (JPQL) so this async/scheduled path can't
        // pick up an import row that belongs to a different tenant — relevant if
        // an attacker ever managed to enqueue a foreign import id.
        val dataImport = dataImportRepository.findActiveById(importId) ?: return
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(
        importId: UUID,
        errors: List<Map<String, String>>,
    ) {
        val dataImport = dataImportRepository.findActiveById(importId) ?: return
        dataImport.status = ImportStatus.FAILED
        dataImport.errors = errors
        dataImport.errorCount = errors.size
        dataImport.successCount = 0
        dataImportRepository.save(dataImport)
    }
}
