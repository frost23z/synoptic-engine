package com.synopticengine.api.auth.service

import com.synopticengine.api.shared.web.RateLimitedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class ForgotPasswordAttemptTracker(
    @Value("\${synoptic.auth.forgot-password.max-attempts:5}") private val maxAttempts: Int,
    @Value("\${synoptic.auth.forgot-password.window-minutes:15}") private val windowMinutes: Long,
    @Value("\${synoptic.auth.forgot-password.lockout-minutes:15}") private val lockoutMinutes: Long,
) {
    private data class State(
        var attemptCount: Int,
        var windowStart: Instant,
        var lockedUntil: Instant?,
    )

    private val states = ConcurrentHashMap<String, State>()

    fun assertNotLocked(
        email: String,
        clientIp: String?,
    ) {
        val state = states[keyFor(email, clientIp)] ?: return
        val lockedUntil = state.lockedUntil ?: return
        val now = Instant.now()
        if (now.isBefore(lockedUntil)) {
            val remainingMillis = Duration.between(now, lockedUntil).toMillis().coerceAtLeast(1)
            val remainingSeconds = ((remainingMillis + 999) / 1000).coerceAtLeast(1)
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            val waitText =
                if (minutes > 0) {
                    "$minutes minute(s) $seconds second(s)"
                } else {
                    "$seconds second(s)"
                }
            throw RateLimitedException(
                "Too many forgot-password attempts. Try again in $waitText.",
            )
        }
        state.lockedUntil = null
        state.attemptCount = 0
        state.windowStart = now
    }

    fun recordAttempt(
        email: String,
        clientIp: String?,
    ) {
        val now = Instant.now()
        states.compute(keyFor(email, clientIp)) { _, existing ->
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

    private fun keyFor(
        email: String,
        clientIp: String?,
    ): String = "${email.lowercase()}|${clientIp ?: "unknown"}"
}
