package com.synopticengine.api.settings.webform.web

import com.synopticengine.api.settings.webform.service.WebFormCaptchaVerifier
import com.synopticengine.api.settings.webform.service.WebFormRateLimiter
import com.synopticengine.api.settings.webform.service.WebFormService
import com.synopticengine.api.shared.webform.WebFormSubmissionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping($$"${api.base-path}/settings/web-forms")
class WebFormController(
    private val webFormService: WebFormService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('settings.view')")
    fun listAll(): ResponseEntity<List<WebFormResponse>> = ResponseEntity.ok(webFormService.findAll())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.view')")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<WebFormResponse> = ResponseEntity.ok(webFormService.findById(id))

    @PostMapping
    @PreAuthorize("hasAuthority('settings.edit')")
    fun create(
        @Valid @RequestBody request: CreateWebFormRequest,
    ): ResponseEntity<WebFormResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                webFormService.create(
                    request.title,
                    request.description,
                    request.isActive,
                    request.createLead,
                    request.backgroundColor,
                    request.submitSuccessAction,
                    request.submitSuccessMessage,
                    request.submitSuccessUrl,
                    request.captchaEnabled,
                    request.fields,
                ),
            )

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.edit')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWebFormRequest,
    ): ResponseEntity<WebFormResponse> =
        ResponseEntity.ok(
            webFormService.update(
                id,
                request.title,
                request.description,
                request.isActive,
                request.createLead,
                request.backgroundColor,
                request.submitSuccessAction,
                request.submitSuccessMessage,
                request.submitSuccessUrl,
                request.captchaEnabled,
                request.fields,
            ),
        )

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.edit')")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        webFormService.delete(id)
        return ResponseEntity.noContent().build()
    }
}

/**
 * Phase 3 / P3.5 — public, unauthenticated endpoint for web form rendering and
 * submission. Allow-listed in [com.synopticengine.api.auth.config.SecurityConfig].
 */
@RestController
@RequestMapping("/web-forms")
class PublicWebFormController(
    private val webFormService: WebFormService,
    private val submissionService: WebFormSubmissionService,
    private val rateLimiter: WebFormRateLimiter,
    private val captchaVerifier: WebFormCaptchaVerifier,
) {
    @GetMapping("/{id}")
    fun getPublicForm(
        @PathVariable id: UUID,
    ): ResponseEntity<WebFormResponse> = ResponseEntity.ok(webFormService.findPublicById(id))

    @PostMapping("/{id}/submit")
    fun submit(
        @PathVariable id: UUID,
        @RequestBody request: WebFormSubmitRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<WebFormSubmitResponse> {
        val ip = clientIp(servletRequest)
        if (!rateLimiter.tryAcquire(ip)) {
            return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(WebFormSubmitResponse(success = false, message = "Rate limit exceeded for $ip"))
        }
        val form = webFormService.findPublicById(id)
        if (form.captchaEnabled && !captchaVerifier.verify(request.captchaToken, ip)) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(WebFormSubmitResponse(success = false, message = "Captcha verification failed"))
        }
        val payload =
            com.synopticengine.api.shared.webform
                .WebFormSubmitPayload(values = request.values)
        val result = submissionService.submit(form.id, payload)
        return ResponseEntity.ok(
            WebFormSubmitResponse(
                success = result.success,
                message = result.message,
                personId = result.personId,
                leadId = result.leadId,
            ),
        )
    }

    /**
     * Honour `X-Forwarded-For` when present (load balancer in front) but never
     * trust its tail past the first hop — only the left-most entry is the
     * original client. Falls back to the socket address otherwise.
     */
    private fun clientIp(req: HttpServletRequest): String {
        val xff = req.getHeader("X-Forwarded-For")
        if (!xff.isNullOrBlank()) return xff.split(",").first().trim()
        return req.remoteAddr ?: "unknown"
    }
}
