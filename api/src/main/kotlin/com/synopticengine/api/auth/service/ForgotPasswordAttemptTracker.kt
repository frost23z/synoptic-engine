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
 * In-memory forgot-password attempt tracker, backed by a bounded Caffeine cache.
 *
 * Identical design to [LoginAttemptTracker] — see that class for architecture notes.
 * Separate bean so the two trackers can have independent configuration (forgot-password
 * is slower-burn; login is fast-cycle).
 *
 * // MULTI-NODE: swap with a Redis-backed [RateLimiter] implementation without
 * // changing call sites.
 */
@Component
class ForgotPasswordAttemptTracker(
    @Value("\${synoptic.auth.forgot-password.max-attempts:5}") private val maxAttempts: Int,
    @Value("\${synoptic.auth.forgot-password.window-minutes:15}") private val windowMinutes: Long,
    @Value("\${synoptic.auth.forgot-password.lockout-minutes:15}") private val lockoutMinutes: Long,
    @Value("\${synoptic.auth.forgot-password.maximum-size:10000}") private val maximumSize: Long,
) : RateLimiter {
    private data class State(
        var attemptCount: Int,
        var windowStart: Instant,
        var lockedUntil: Instant?,
    )

    private val cache: Cache<String, State> =
        Caffeine
            .newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(lockoutMinutes + windowMinutes, TimeUnit.MINUTES)
            .build()

    override fun assertNotLocked(
        email: String,
        clientIp: String?,
    ) {
        val key = keyFor(email, clientIp)
        var shouldThrow = false
        var remainingMillis = 0L
        cache.asMap().compute(key) { _, existing ->
            val state = existing ?: return@compute null
            val lockedUntil = state.lockedUntil ?: return@compute state
            val now = Instant.now()
            if (now.isBefore(lockedUntil)) {
                shouldThrow = true
                remainingMillis = Duration.between(now, lockedUntil).toMillis().coerceAtLeast(1)
                state
            } else {
                State(attemptCount = 0, windowStart = now, lockedUntil = null)
            }
        }
        if (shouldThrow) {
            val remainingSeconds = ((remainingMillis + 999) / 1000).coerceAtLeast(1)
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            val waitText =
                if (minutes > 0) "$minutes minute(s) $seconds second(s)" else "$seconds second(s)"
            throw RateLimitedException("Too many forgot-password attempts. Try again in $waitText.")
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
            val s = existing ?: State(attemptCount = 0, windowStart = now, lockedUntil = null)
            if (Duration.between(s.windowStart, now) > window) {
                s.attemptCount = 0
                s.windowStart = now
            }
            s.attemptCount += 1
            if (s.attemptCount >= maxAttempts) {
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
