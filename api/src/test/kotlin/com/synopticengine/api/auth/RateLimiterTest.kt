package com.synopticengine.api.auth

import com.synopticengine.api.auth.service.LoginAttemptTracker
import com.synopticengine.api.shared.web.RateLimitedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for the Caffeine-backed rate limiter (T1.4 - auth hardening).
 *
 * Pure-unit tests: no Spring context, no Testcontainers.
 * Run via `./gradlew unitTests`.
 */
class RateLimiterTest {
    private fun tracker(
        maxFailures: Int = 3,
        windowMinutes: Long = 15,
        lockoutMinutes: Long = 15,
        maximumSize: Long = 100,
    ) = LoginAttemptTracker(maxFailures, windowMinutes, lockoutMinutes, maximumSize)

    @Test
    fun `no failures - assertNotLocked does not throw`() {
        val t = tracker()
        assertDoesNotThrow { t.assertNotLocked("alice@example.com", "1.2.3.4") }
    }

    @Test
    fun `below threshold - assertNotLocked does not throw`() {
        val t = tracker(maxFailures = 3)
        t.recordFailure("alice@example.com", "1.2.3.4")
        t.recordFailure("alice@example.com", "1.2.3.4")
        // 2 failures < 3 threshold
        assertDoesNotThrow { t.assertNotLocked("alice@example.com", "1.2.3.4") }
    }

    @Test
    fun `at threshold - assertNotLocked throws RateLimitedException`() {
        val t = tracker(maxFailures = 3)
        repeat(3) { t.recordFailure("bob@example.com", "1.2.3.4") }
        assertThrows<RateLimitedException> { t.assertNotLocked("bob@example.com", "1.2.3.4") }
    }

    @Test
    fun `success clears failure count - subsequent failures start fresh`() {
        val t = tracker(maxFailures = 3)
        repeat(2) { t.recordFailure("carol@example.com", null) }
        t.recordSuccess("carol@example.com", null)
        // After success, back to zero.
        assertDoesNotThrow { t.assertNotLocked("carol@example.com", null) }
        // Add 2 more - still below threshold.
        repeat(2) { t.recordFailure("carol@example.com", null) }
        assertDoesNotThrow { t.assertNotLocked("carol@example.com", null) }
    }

    @Test
    fun `different (email, ip) keys are tracked independently`() {
        val t = tracker(maxFailures = 3)
        repeat(3) { t.recordFailure("dave@example.com", "1.2.3.4") }
        // Same email, different IP - should not be locked.
        assertDoesNotThrow { t.assertNotLocked("dave@example.com", "5.6.7.8") }
        // Different email, same IP - should not be locked.
        assertDoesNotThrow { t.assertNotLocked("other@example.com", "1.2.3.4") }
    }

    @Test
    fun `null clientIp is keyed separately from a real IP`() {
        val t = tracker(maxFailures = 2)
        repeat(2) { t.recordFailure("eve@example.com", null) }
        // null IP is locked
        assertThrows<RateLimitedException> { t.assertNotLocked("eve@example.com", null) }
        // real IP is not locked
        assertDoesNotThrow { t.assertNotLocked("eve@example.com", "9.9.9.9") }
    }

    @Test
    fun `email comparison is case-insensitive`() {
        val t = tracker(maxFailures = 2)
        t.recordFailure("Frank@Example.COM", "1.1.1.1")
        t.recordFailure("FRANK@example.com", "1.1.1.1")
        assertThrows<RateLimitedException> { t.assertNotLocked("frank@example.com", "1.1.1.1") }
    }

    @Test
    fun `bounded cache does not throw when size limit is reached`() {
        val t = tracker(maxFailures = 1, maximumSize = 5)
        // Insert more than maximumSize unique keys - Caffeine evicts silently; no error.
        repeat(20) { i -> t.recordFailure("user$i@test.com", "1.1.1.1") }
        // Spot-check: the tracker does not crash and behaves correctly for keys still in cache.
        // We can't predict which entries were evicted, so just assert no exception.
    }
}
