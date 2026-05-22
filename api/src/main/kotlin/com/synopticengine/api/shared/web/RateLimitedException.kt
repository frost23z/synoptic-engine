package com.synopticengine.api.shared.web

/**
 * Thrown when a caller hits a rate-limit / lockout. Mapped to HTTP 429
 * Too Many Requests by [GlobalExceptionHandler]. Lives in the shared
 * module so any module can throw it without dragging the shared
 * exception handler into module-specific imports (Spring Modulith
 * forbids that).
 */
class RateLimitedException(
    message: String,
) : RuntimeException(message)
