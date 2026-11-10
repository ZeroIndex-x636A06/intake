package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `only rate limits and server errors are worth retrying`() {
        listOf(429, 500, 502, 503, 504).forEach { assertThat(RetryPolicy.isRetryable(it)).isTrue() }
        listOf(200, 400, 401, 402, 403, 404, 408, 422, 501).forEach { assertThat(RetryPolicy.isRetryable(it)).isFalse() }
    }

    @Test
    fun `retry-after in seconds is honoured exactly`() {
        assertThat(RetryPolicy.parseRetryAfter("7", 0)).isEqualTo(7_000)
        assertThat(RetryPolicy.parseRetryAfter(" 2.5 ", 0)).isEqualTo(2_500)
        assertThat(RetryPolicy.parseRetryAfter("0", 0)).isEqualTo(0)
    }

    @Test
    fun `retry-after as an http date is measured from now`() {
        val now = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneOffset.UTC)
        val at = now.plusSeconds(20)
        val header = DateTimeFormatter.RFC_1123_DATE_TIME.format(at)
        assertThat(RetryPolicy.parseRetryAfter(header, now.toInstant().toEpochMilli())).isEqualTo(20_000)
        // A date in the past means "now".
        assertThat(RetryPolicy.parseRetryAfter(header, at.plusSeconds(5).toInstant().toEpochMilli())).isEqualTo(0)
    }

    @Test
    fun `a missing or unreadable retry-after is null`() {
        assertThat(RetryPolicy.parseRetryAfter(null, 0)).isNull()
        assertThat(RetryPolicy.parseRetryAfter("", 0)).isNull()
        assertThat(RetryPolicy.parseRetryAfter("soon", 0)).isNull()
    }

    @Test
    fun `without a retry-after the wait doubles each time`() {
        assertThat(RetryPolicy.waitMs(1, null, jitter = 0.5)).isEqualTo(1_000)
        assertThat(RetryPolicy.waitMs(2, null, jitter = 0.5)).isEqualTo(2_000)
    }

    @Test
    fun `jitter spreads the wait by a quarter either way`() {
        assertThat(RetryPolicy.waitMs(1, null, jitter = 0.0)).isEqualTo(750)
        assertThat(RetryPolicy.waitMs(1, null, jitter = 1.0)).isEqualTo(1_250)
        assertThat(RetryPolicy.waitMs(2, null, jitter = 0.0)).isEqualTo(1_500)
    }

    @Test
    fun `the servers retry-after wins over our own backoff`() {
        assertThat(RetryPolicy.waitMs(1, 7_000, jitter = 0.0)).isEqualTo(7_000)
        assertThat(RetryPolicy.waitMs(2, 3_000, jitter = 1.0)).isEqualTo(3_250)
    }

    @Test
    fun `a retry-after longer than the cap gives up instead of waiting inside a user action`() {
        assertThat(RetryPolicy.waitMs(1, RetryPolicy.MAX_WAIT_MS + 1, jitter = 0.0)).isNull()
        assertThat(RetryPolicy.waitMs(1, RetryPolicy.MAX_WAIT_MS, jitter = 0.0)).isEqualTo(RetryPolicy.MAX_WAIT_MS)
    }

    @Test
    fun `after the last attempt there is no wait`() {
        assertThat(RetryPolicy.waitMs(RetryPolicy.MAX_ATTEMPTS, null, jitter = 0.5)).isNull()
        assertThat(RetryPolicy.waitMs(RetryPolicy.MAX_ATTEMPTS, 1_000, jitter = 0.5)).isNull()
    }

    @Test
    fun `an out of range jitter is clamped`() {
        assertThat(RetryPolicy.waitMs(1, null, jitter = -5.0)).isEqualTo(750)
        assertThat(RetryPolicy.waitMs(1, null, jitter = 9.0)).isEqualTo(1_250)
    }
}
