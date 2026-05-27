package com.synopticengine.api.auth.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.synopticengine.api.shared.auth.RateLimiter
import com.synopticengine.api.shared.web.RateLimitedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * In-memory login attempt tracker (H6 — login rate limit + lockout).
 *
 * Strategy: count consecutive failed attempts per `(email, clientIp)` key.
 * After [maxFailures] failures within [windowMinutes] the key is locked for
 * [lockoutMinutes]. A successful login resets the count.
 *
 * Backed by a bounded Caffeine cache (max [maximumSize] keys, auto-expired
 * after write) to prevent unbounded memory growth from probing attacks.
 * All state mutations are performed inside `cache.asMap().compute()` to
 * prevent read-modify-write races.
 *
 * // MULTI-NODE: swap [LoginAttemptTracker] with a Redis-backed [RateLimiter]
 * // implementation; no call-site changes needed.
 *
 * Defaults: 5 failures in 15 minutes = 15-minute lockout, configurable via
 * `synoptic.auth.lockout.*` properties.
 */
@Component
class LoginAttemptTracker(
    @Value("\${synoptic.auth.lockout.max-failures:5}") private val maxFailures: Int,
    @Value("\${synoptic.auth.lockout.window-minutes:15}") private val windowMinutes: Long,
    @Value("\${synoptic.auth.lockout.lockout-minutes:15}") private val lockoutMinutes: Long,
    @Value("\${synoptic.auth.lockout.maximum-size:10000}") private val maximumSize: Long,
) : RateLimiter {
    private data class State(
        var failureCount: Int,
        var windowStart: Instant,
        var lockedUntil: Instant?,
    )

    // Bounded Caffeine cache — entries auto-expire after write so locked entries
    // can't accumulate indefinitely, and the map size is capped against OOM.
    private val cache: Cache<String, State> =
        Caffeine
            .newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(lockoutMinutes + windowMinutes, TimeUnit.MINUTES)
            .build()

    /**
     * Throws [RateLimitedException] when the caller is currently locked.
     * Call this BEFORE attempting the credential check so brute-force callers
     * can't even reach the password verifier.
     */
    override fun assertNotLocked(
        email: String,
        clientIp: String?,
    ) {
        val key = keyFor(email, clientIp)
        // Atomic compute: read and potentially clear the expired lock in one step.
        var shouldThrow = false
        var remainingMinutes = 0L
        cache.asMap().compute(key) { _, existing ->
            val state = existing ?: return@compute null
            val lockedUntil = state.lockedUntil ?: return@compute state
            val now = Instant.now()
            if (now.isBefore(lockedUntil)) {
                shouldThrow = true
                remainingMinutes = Duration.between(now, lockedUntil).toMinutes() + 1
                state
            } else {
                // Lock expired — reset lazily.
                State(failureCount = 0, windowStart = now, lockedUntil = null)
            }
        }
        if (shouldThrow) {
            throw RateLimitedException(
                "Too many failed login attempts. Try again in $remainingMinutes minute(s).",
            )
        }
    }

    override fun recordFailure(
        email: String,
        clientIp: String?,
    ) {
        val now = Instant.now()
        val key = keyFor(email, clientIp)
        cache.asMap().compute(key) { _, existing ->
            val window = Duration.ofMinutes(windowMinutes)
            val s = existing ?: State(failureCount = 0, windowStart = now, lockedUntil = null)
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
    }

    override fun recordSuccess(
        email: String,
        clientIp: String?,
    ) {
        cache.invalidate(keyFor(email, clientIp))
    }

    private fun keyFor(
        email: String,
        clientIp: String?,
    ): String = "${email.lowercase()}|${clientIp ?: "unknown"}"
}
