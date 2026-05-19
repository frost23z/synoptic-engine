package com.synopticengine.api.crm.quote.web

import com.synopticengine.api.crm.quote.domain.QuoteStatus
import com.synopticengine.api.crm.quote.service.QuotePdfService
import com.synopticengine.api.crm.quote.service.QuoteService
import com.synopticengine.api.shared.web.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/quotes")
class QuoteController(
    private val quoteService: QuoteService,
    private val quotePdfService: QuotePdfService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('quotes.view')")
    fun listAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam leadId: UUID?,
        @RequestParam status: QuoteStatus?,
        @RequestParam(defaultValue = "false") expiredOnly: Boolean,
    ): ResponseEntity<PageResponse<QuoteResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return if (expiredOnly) {
            ResponseEntity.ok(quoteService.listExpired(pageable))
        } else {
            ResponseEntity.ok(quoteService.filter(leadId, status, pageable))
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('quotes.view')")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<QuoteResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        return ResponseEntity.ok(quoteService.search(q, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('quotes.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<QuoteResponse> = ResponseEntity.ok(quoteService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('quotes.create')")
    fun create(
        @Valid @RequestBody request: CreateQuoteRequest,
    ): ResponseEntity<QuoteResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                quoteService.create(
                    request.leadId,
                    request.title,
                    request.userId,
                    request.discount,
                    request.tax,
                    request.terms,
                    request.expiredAt,
                    request.items,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('quotes.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateQuoteRequest,
    ): ResponseEntity<QuoteResponse> =
        ResponseEntity.ok(
            quoteService.update(
                id,
                request.title,
                request.userId,
                request.discount,
                request.tax,
                request.terms,
                request.expiredAt,
                request.items,
            ),
        )

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('quotes.edit')")
    fun updateStatus(
        @PathVariable id: UUID,
        @RequestBody request: UpdateQuoteStatusRequest,
    ): ResponseEntity<QuoteResponse> = ResponseEntity.ok(quoteService.updateStatus(id, request.status))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('quotes.delete')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        quoteService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mass-destroy")
    @PreAuthorize("hasAuthority('quotes.delete')")
    fun massDestroy(
        @RequestBody request: MassDestroyQuoteRequest,
    ): ResponseEntity<Void> {
        quoteService.massDestroy(request.ids)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('quotes.edit')")
    fun duplicate(
        @PathVariable id: UUID,
    ): ResponseEntity<QuoteResponse> = ResponseEntity.ok(quoteService.duplicate(id))

    @PostMapping("/{id}/send-mail")
    @PreAuthorize("hasAuthority('quotes.edit')")
    fun sendMail(
        @PathVariable id: UUID,
        @RequestBody request: SendQuoteMailRequest,
    ): ResponseEntity<SendQuoteMailResponse> =
        ResponseEntity.ok(quoteService.sendMail(id, request.to, request.subject, request.message))

    @GetMapping("/lead-products/{leadId}")
    @PreAuthorize("hasAuthority('quotes.view')")
    fun getLeadProducts(
        @PathVariable leadId: UUID,
    ): ResponseEntity<List<QuoteLeadProductResponse>> = ResponseEntity.ok(quoteService.getLeadProducts(leadId))

    @GetMapping("/{id}/print")
    @PreAuthorize("hasAuthority('quotes.view')")
    fun print(
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> {
        val quote = quoteService.findById(id)
        val pdf = quotePdfService.generate(quote)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.contentDisposition =
            ContentDisposition
                .attachment()
                .filename("quote-$id.pdf")
                .build()
        return ResponseEntity.ok().headers(headers).body(pdf)
    }
}
