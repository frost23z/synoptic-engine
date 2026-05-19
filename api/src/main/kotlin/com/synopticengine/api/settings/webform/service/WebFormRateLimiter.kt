package com.synopticengine.api.settings.webform.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Krayin's public submit endpoint throttles to 100 requests per 60 seconds
 * per IP. We mirror that with a per-IP sliding window — every accepted
 * submission records its timestamp; we evict everything older than the window
 * and reject when the deque already holds [maxPerWindow].
 *
 * Trivial in-memory implementation: a single instance covers single-node
 * deployments; once we run multiple API instances behind a load balancer
 * this needs to be swapped for a Redis-backed counter or front-loaded to
 * a gateway. Documented in `02 § 2.6`.
 */
@Component
class WebFormRateLimiter(
    @Value("\${webform.rate-limit.max:100}") private val maxPerWindow: Int,
    @Value("\${webform.rate-limit.window-seconds:60}") private val windowSeconds: Long,
) {
    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /**
     * Returns true when the request is allowed; false when over the limit.
     * Accepted requests are recorded as they pass through.
     */
    fun tryAcquire(ip: String): Boolean {
        val nowMillis = System.currentTimeMillis()
        val windowMillis = windowSeconds * 1000
        val deque = buckets.computeIfAbsent(ip) { ArrayDeque() }
        synchronized(deque) {
            while (deque.peekFirst()?.let { it < nowMillis - windowMillis } == true) {
                deque.pollFirst()
            }
            if (deque.size >= maxPerWindow) return false
            deque.addLast(nowMillis)
            return true
        }
    }
}
