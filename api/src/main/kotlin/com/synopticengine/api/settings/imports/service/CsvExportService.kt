package com.synopticengine.api.settings.imports.service

import com.synopticengine.api.crm.CrmApi
import com.synopticengine.api.inventory.InventoryApi
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * CSV exporters that stream rows directly to a caller-supplied [OutputStream], paginating
 * the underlying DB scan so the full tenant never lands in heap at once. For backwards
 * compatibility the `exportXxx(): String` flavour is kept for callers that genuinely
 * want the whole result in memory — but every controller has been migrated to streaming.
 */
@Service
class CsvExportService(
    private val crmApi: CrmApi,
    private val inventoryApi: InventoryApi,
) {
    fun streamPersons(out: OutputStream) {
        writeCsv(out, listOf("id", "firstName", "lastName", "email", "phone", "jobTitle")) { printer ->
            crmApi.streamPersonsCsv { p ->
                printer.printRecord(p.id, p.firstName, p.lastName, p.email ?: "", p.phone ?: "", p.jobTitle ?: "")
            }
        }
    }

    fun streamOrganizations(out: OutputStream) {
        writeCsv(out, listOf("id", "name", "email", "phone", "website", "address")) { printer ->
            crmApi.streamOrganizationsCsv { o ->
                printer.printRecord(o.id, o.name, o.email ?: "", o.phone ?: "", o.website ?: "", o.address ?: "")
            }
        }
    }

    fun streamLeads(out: OutputStream) {
        writeCsv(out, listOf("id", "title", "status", "amount", "pipelineId", "stageId")) { printer ->
            crmApi.streamLeadsCsv { l ->
                printer.printRecord(l.id, l.title, l.status, l.amount ?: "", l.pipelineId, l.stageId)
            }
        }
    }

    fun streamProducts(out: OutputStream) {
        writeCsv(out, listOf("id", "name", "sku", "price", "description")) { printer ->
            inventoryApi.streamProductsCsv { p ->
                printer.printRecord(p.id, p.name, p.sku ?: "", p.price, p.description ?: "")
            }
        }
    }

    private inline fun writeCsv(
        out: OutputStream,
        headers: List<String>,
        block: (CSVPrinter) -> Unit,
    ) {
        val writer = OutputStreamWriter(out, StandardCharsets.UTF_8)
        CSVPrinter(
            writer,
            CSVFormat.DEFAULT
                .builder()
                .setHeader(*headers.toTypedArray())
                .build(),
        ).use { printer ->
            block(printer)
            printer.flush()
        }
        // CSVPrinter.close() (triggered by use {}) closes the underlying writer
        // because OutputStreamWriter is Closeable — that close call flushes the
        // writer's buffer to `out`. Don't flush() again here: the writer is closed
        // and the JDK's StreamEncoder throws IOException("Stream closed").
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
