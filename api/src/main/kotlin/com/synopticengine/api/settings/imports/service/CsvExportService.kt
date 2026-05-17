package com.synopticengine.api.settings.imports.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.inventory.InventoryApi
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.stereotype.Service
import java.io.StringWriter

@Service
class CsvExportService(
    private val crmApi: CrmApi,
    private val inventoryApi: InventoryApi,
) {
    fun exportPersons(): String {
        val writer = StringWriter()
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT
                .builder()
                .setHeader("id", "firstName", "lastName", "email", "phone", "jobTitle")
                .build(),
        ).use { printer ->
            crmApi.exportPersonsCsv().forEach { p ->
                printer.printRecord(p.id, p.firstName, p.lastName, p.email ?: "", p.phone ?: "", p.jobTitle ?: "")
            }
        }
        return writer.toString()
    }

    fun exportOrganizations(): String {
        val writer = StringWriter()
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT
                .builder()
                .setHeader("id", "name", "email", "phone", "website", "address")
                .build(),
        ).use { printer ->
            crmApi.exportOrganizationsCsv().forEach { o ->
                printer.printRecord(o.id, o.name, o.email ?: "", o.phone ?: "", o.website ?: "", o.address ?: "")
            }
        }
        return writer.toString()
    }

    fun exportLeads(): String {
        val writer = StringWriter()
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT
                .builder()
                .setHeader("id", "title", "status", "amount", "pipelineId", "stageId")
                .build(),
        ).use { printer ->
            crmApi.exportLeadsCsv().forEach { l ->
                printer.printRecord(l.id, l.title, l.status, l.amount ?: "", l.pipelineId, l.stageId)
            }
        }
        return writer.toString()
    }

    fun exportProducts(): String {
        val writer = StringWriter()
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT
                .builder()
                .setHeader("id", "name", "sku", "price", "description")
                .build(),
        ).use { printer ->
            inventoryApi.exportProductsCsv().forEach { p ->
                printer.printRecord(p.id, p.name, p.sku ?: "", p.price, p.description ?: "")
            }
        }
        return writer.toString()
    }

    fun sampleCsv(entityType: String): String =
        when (entityType.lowercase()) {
            "person" -> {
                buildString {
                    appendLine("firstName,lastName,email,phone,jobTitle")
                    appendLine("John,Doe,john@example.com,+1234567890,Manager")
                }
            }

            "lead" -> {
                buildString {
                    appendLine("title,description,amount,pipelineId,stageId")
                    appendLine(
                        "Deal with Acme,Follow up with client,5000," +
                            "00000000-0000-0000-0000-000000000010," +
                            "00000000-0000-0000-0000-000000000011",
                    )
                }
            }

            "product" -> {
                buildString {
                    appendLine("name,sku,price,description")
                    appendLine("Product A,SKU-001,99.99,A sample product")
                }
            }

            else -> {
                throw NoSuchElementException("Unknown entity type: $entityType")
            }
        }
}
