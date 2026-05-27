package com.synopticengine.api.shared.auth

import com.synopticengine.api.shared.web.RateLimitedException

/**
 * Contract for per-(email, IP) attempt tracking and rate limiting.
 *
 * The single-node implementation ([CaffeineRateLimiter]) uses a bounded Caffeine
 * cache so the in-process map is protected against OOM from unbounded probing.
 *
 * // MULTI-NODE: swap in a Redis/bucket4j-backed implementation without changing
 * // any call sites. The interface keys on (email, clientIp) pairs so a distributed
 * // impl can shard by email across nodes.
 */
interface RateLimiter {
    /**
     * Throws [RateLimitedException] when the (email, clientIp) pair is currently
     * locked. Call this **before** any credential lookup so brute-force callers
     * cannot use response-time differences to detect valid accounts.
     */
    fun assertNotLocked(
        email: String,
        clientIp: String?,
    )

    /** Record a failed attempt for the (email, clientIp) pair. */
    fun recordFailure(
        email: String,
        clientIp: String?,
    )

    /** Record a successful attempt and clear any accumulated failure count. */
    fun recordSuccess(
        email: String,
        clientIp: String?,
    )
}
