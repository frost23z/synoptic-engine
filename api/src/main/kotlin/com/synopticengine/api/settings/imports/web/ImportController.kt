package com.synopticengine.api.settings.imports.web

import com.synopticengine.api.settings.imports.service.CsvExportService
import com.synopticengine.api.settings.imports.service.DataImportService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/settings/imports")
class ImportController(
    private val dataImportService: DataImportService,
    private val csvExportService: CsvExportService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('imports.view')")
    fun listAll(): ResponseEntity<List<DataImportResponse>> = ResponseEntity.ok(dataImportService.findAll())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('imports.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<DataImportResponse> = ResponseEntity.ok(dataImportService.findById(id))

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('imports.create')")
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam entityType: String,
    ): ResponseEntity<DataImportResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(dataImportService.upload(file, entityType))

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('imports.edit')")
    fun start(
        @PathVariable id: UUID,
    ): ResponseEntity<DataImportResponse> = ResponseEntity.ok(dataImportService.startImport(id))

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAuthority('imports.view')")
    fun stats(
        @PathVariable id: UUID,
    ): ResponseEntity<DataImportStatsResponse> = ResponseEntity.ok(dataImportService.getStats(id))

    @GetMapping("/{id}/download-errors")
    @PreAuthorize("hasAuthority('imports.view')")
    fun downloadErrors(
        @PathVariable id: UUID,
    ): ResponseEntity<String> {
        val csv = dataImportService.downloadErrorsCsv(id)
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.parseMediaType("text/csv")
                contentDisposition = ContentDisposition.attachment().filename("import-errors-$id.csv").build()
            }
        return ResponseEntity.ok().headers(headers).body(csv)
    }

    @GetMapping("/sample/{entityType}")
    @PreAuthorize("hasAuthority('imports.view')")
    fun sampleCsv(
        @PathVariable entityType: String,
    ): ResponseEntity<String> {
        val csv = csvExportService.sampleCsv(entityType)
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.parseMediaType("text/csv")
                contentDisposition = ContentDisposition.attachment().filename("sample-$entityType.csv").build()
            }
        return ResponseEntity.ok().headers(headers).body(csv)
    }

    @GetMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('imports.edit')")
    fun validate(
        @PathVariable id: UUID,
    ): ResponseEntity<DataImportResponse> = ResponseEntity.ok(dataImportService.validate(id))

    @GetMapping("/{id}/link")
    @PreAuthorize("hasAuthority('imports.edit')")
    fun link(
        @PathVariable id: UUID,
    ): ResponseEntity<DataImportResponse> = ResponseEntity.ok(dataImportService.link(id))

    @GetMapping("/{id}/index-data")
    @PreAuthorize("hasAuthority('imports.edit')")
    fun indexData(
        @PathVariable id: UUID,
    ): ResponseEntity<DataImportResponse> = ResponseEntity.ok(dataImportService.indexData(id))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('imports.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        dataImportService.delete(id)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping($$"${api.base-path}")
class CsvExportController(
    private val csvExportService: CsvExportService,
) {
    @GetMapping("/persons/export")
    @PreAuthorize("hasAuthority('contacts.view')")
    fun exportPersons(): ResponseEntity<ByteArray> = csvResponse("persons.csv") { csvExportService.streamPersons(it) }

    @GetMapping("/organizations/export")
    @PreAuthorize("hasAuthority('contacts.view')")
    fun exportOrganizations(): ResponseEntity<ByteArray> =
        csvResponse("organizations.csv") { csvExportService.streamOrganizations(it) }

    @GetMapping("/leads/export")
    @PreAuthorize("hasAuthority('leads.view')")
    fun exportLeads(): ResponseEntity<ByteArray> = csvResponse("leads.csv") { csvExportService.streamLeads(it) }

    @GetMapping("/products/export")
    @PreAuthorize("hasAuthority('products.view')")
    fun exportProducts(): ResponseEntity<ByteArray> =
        csvResponse("products.csv") { csvExportService.streamProducts(it) }

    /**
     * Buffer the CSV into a ByteArray rather than streaming via StreamingResponseBody —
     * the buffer is bounded by row count, but more importantly the DB-level pagination
     * inside [csvExportService] means Hibernate entities for the full tenant are never
     * resident at once. Future change: switch to StreamingResponseBody when integration
     * tests can read the async response body cleanly.
     */
    private fun csvResponse(
        filename: String,
        write: (java.io.OutputStream) -> Unit,
    ): ResponseEntity<ByteArray> {
        val buffer = java.io.ByteArrayOutputStream()
        write(buffer)
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.parseMediaType("text/csv")
                contentDisposition = ContentDisposition.attachment().filename(filename).build()
            }
        return ResponseEntity.ok().headers(headers).body(buffer.toByteArray())
    }
}
