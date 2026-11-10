package com.zeroindex.intake.domain.ai

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

/**
 * When and how long to wait before sending a request again after the provider pushed back (HTTP 429 or a
 * 5xx). Pure, so the policy is tested without a network or a clock.
 *
 * A server's `Retry-After` always wins over our own guess. If it asks for longer than [MAX_WAIT_MS] we do not
 * sit and wait inside a user action: the failure is reported as "rate limited, try again in a minute".
 * Timeouts and dropped connections are never retried here: the request may already have been billed.
 */
object RetryPolicy {
    /** One try plus two retries. */
    const val MAX_ATTEMPTS = 3
    const val MAX_WAIT_MS = 30_000L
    private const val BASE_MS = 1_000L

    fun isRetryable(status: Int): Boolean = status == 429 || status == 500 || status in 502..504

    /** `Retry-After` is either whole seconds or an HTTP date. Returns milliseconds, or null when absent or unreadable. */
    fun parseRetryAfter(header: String?, nowMillis: Long): Long? {
        val v = header?.trim().orEmpty()
        if (v.isEmpty()) return null
        v.toDoubleOrNull()?.let { return (it * 1_000).toLong().coerceAtLeast(0) }
        return runCatching {
            val at = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            (at - nowMillis).coerceAtLeast(0)
        }.getOrNull()
    }

    /**
     * How long to wait after failed attempt number [attempt] (1 is the first), or null to stop retrying.
     * [jitter] is a number in 0..1 (pass a random one) that spreads clients apart so they do not retry in step.
     */
    fun waitMs(attempt: Int, retryAfterMs: Long?, jitter: Double): Long? {
        if (attempt >= MAX_ATTEMPTS) return null
        val j = jitter.coerceIn(0.0, 1.0)
        if (retryAfterMs != null) {
            if (retryAfterMs > MAX_WAIT_MS) return null
            return retryAfterMs + (j * 250).toLong()
        }
        val exp = BASE_MS shl (attempt - 1)
        val spread = 0.75 + 0.5 * j
        return min((exp * spread).toLong(), MAX_WAIT_MS)
    }
}
