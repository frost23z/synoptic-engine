package com.synopticengine.api.auth.service

import com.synopticengine.api.shared.web.RateLimitedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory login attempt tracker (H6 — login rate limit + lockout).
 *
 * Strategy: count consecutive failed attempts per `(email, clientIp)` key.
 * After [maxFailures] failures within [windowMinutes] the key is locked for
 * [lockoutMinutes]. A successful login resets the count.
 *
 * This is the simplest sane implementation — single-instance, no cross-JVM
 * coordination. For multi-instance deployments swap in a Redis-backed
 * implementation behind the same interface; the controller and service
 * code don't change.
 *
 * Defaults: 5 failures in 15 minutes = 15-minute lockout, configurable via
 * `synoptic.auth.lockout.*` properties.
 */
@Component
class LoginAttemptTracker(
    @Value("\${synoptic.auth.lockout.max-failures:5}") private val maxFailures: Int,
    @Value("\${synoptic.auth.lockout.window-minutes:15}") private val windowMinutes: Long,
    @Value("\${synoptic.auth.lockout.lockout-minutes:15}") private val lockoutMinutes: Long,
) {
    private data class State(
        var failureCount: Int,
        var windowStart: Instant,
        var lockedUntil: Instant?,
    )

    private val states = ConcurrentHashMap<String, State>()

    /**
     * Throws [RateLimitedException] when the caller is currently locked.
     * Call this BEFORE attempting the credential check so brute-force callers
     * can't even reach the password verifier.
     */
    fun assertNotLocked(
        email: String,
        clientIp: String?,
    ) {
        val state = states[keyFor(email, clientIp)] ?: return
        val lockedUntil = state.lockedUntil ?: return
        val now = Instant.now()
        if (now.isBefore(lockedUntil)) {
            throw RateLimitedException(
                "Too many failed login attempts. Try again in ${Duration.between(now, lockedUntil).toMinutes() + 1} minute(s).",
            )
        }
        // Lock expired — clear it lazily.
        state.lockedUntil = null
        state.failureCount = 0
        state.windowStart = now
    }

    fun recordFailure(
        email: String,
        clientIp: String?,
    ) {
        val now = Instant.now()
        val state =
            states.compute(keyFor(email, clientIp)) { _, existing ->
                val window = Duration.ofMinutes(windowMinutes)
                val s = existing
                    ?: State(failureCount = 0, windowStart = now, lockedUntil = null)
                if (Duration.between(s.windowStart, now) > window) {
                    s.failureCount = 0
                    s.windowStart = now
                }
                s.failureCount += 1
                if (s.failureCount >= maxFailures) {
                    s.lockedUntil = now.plus(Duration.ofMinutes(lockoutMinutes))
                }
                s
            }
        // No-op — `compute` mutated the value in place.
        @Suppress("UNUSED_EXPRESSION") state
    }

    fun recordSuccess(
        email: String,
        clientIp: String?,
    ) {
        states.remove(keyFor(email, clientIp))
    }

    private fun keyFor(
        email: String,
        clientIp: String?,
    ): String = "${email.lowercase()}|${clientIp ?: "unknown"}"
}
