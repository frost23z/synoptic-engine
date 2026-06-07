package com.synopticengine.api.shared.web

import com.synopticengine.api.shared.upload.FileSizeLimitExceededException
import com.synopticengine.api.shared.upload.UnsupportedMediaTypeException
import jakarta.persistence.OptimisticLockException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {
    // 404 — entity not found
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    // 404 — no controller handler / static resource matches the request path.
    // Without this, an unmapped route falls through to the catch-all below and
    // surfaces as a misleading 500. Caught explicitly so unknown paths return 404.
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "No resource found for the requested path")

    // 400 — bad input, wrong IDs, business rule violations (incl. file-size exceeded)
    @ExceptionHandler(IllegalArgumentException::class, FileSizeLimitExceededException::class)
    fun handleBadRequest(ex: RuntimeException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    // 400 — @Validated constraint violations on @RequestParam / @PathVariable
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail {
        val errors =
            ex.constraintViolations.associate { v ->
                val field = v.propertyPath.toString().substringAfterLast('.')
                field to (v.message ?: "Invalid value")
            }
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed")
            .also { it.setProperty("errors", errors) }
    }

    // 415 — unsupported MIME type on file upload (T4.3)
    @ExceptionHandler(UnsupportedMediaTypeException::class)
    fun handleUnsupportedMediaType(ex: UnsupportedMediaTypeException): ProblemDetail =
        problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.message ?: "Unsupported media type")

    // 409 — conflict, duplicate, invalid state transition
    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex.message ?: "Conflict")

    // 409 — optimistic locking failure (concurrent modification)
    @ExceptionHandler(OptimisticLockException::class, ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: Exception): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Record was modified by another user. Please refresh and try again.")

    // 422 — @Valid/@Validated failures on request bodies
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val errors =
            ex.bindingResult.allErrors.associate { error ->
                val field = (error as? FieldError)?.field ?: "unknown"
                field to (error.defaultMessage ?: "Invalid value")
            }
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed")
            .also { it.setProperty("errors", errors) }
    }

    // 403 — authenticated but not authorized
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ProblemDetail = problem(HttpStatus.FORBIDDEN, "Access denied")

    // 401 — not authenticated at all
    // Note: Spring Security handles this via AuthenticationEntryPoint,
    // NOT through @ExceptionHandler — so you don't handle it here

    // 405 — wrong HTTP method
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ProblemDetail =
        problem(HttpStatus.METHOD_NOT_ALLOWED, "Method ${ex.method} not allowed")

    // 429 — rate-limit / lockout (e.g. LoginAttemptTracker)
    @ExceptionHandler(RateLimitedException::class)
    fun handleRateLimited(ex: RateLimitedException): ProblemDetail =
        problem(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Too many requests")

    // 400 — malformed JSON, wrong types in request body
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(ex: HttpMessageNotReadableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Malformed request body")

    // 500 — catch-all, never expose internal details
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
    }

    private fun problem(
        status: HttpStatus,
        detail: String,
    ): ProblemDetail =
        ProblemDetail
            .forStatusAndDetail(status, detail)
            .also { it.type = URI.create("about:blank") }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
