package com.zeroindex.intake.domain.performance

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class JumpMetricsTest {

    @Test
    fun `standing and total give the vertical`() {
        val c = JumpMetrics.complete(vertical = null, standing = 228.6, total = 299.7)
        assertThat(c.derived).isEqualTo(JumpMetrics.Field.VERTICAL)
        assertThat(c.verticalJumpCm!!).isWithin(1e-9).of(71.1)
        assertThat(c.inconsistent).isFalse()
    }

    @Test
    fun `vertical and standing give the total`() {
        val c = JumpMetrics.complete(vertical = 70.0, standing = 230.0, total = null)
        assertThat(c.derived).isEqualTo(JumpMetrics.Field.TOTAL)
        assertThat(c.totalReachCm).isEqualTo(300.0)
    }

    @Test
    fun `vertical and total give the standing reach`() {
        val c = JumpMetrics.complete(vertical = 70.0, standing = null, total = 300.0)
        assertThat(c.derived).isEqualTo(JumpMetrics.Field.STANDING)
        assertThat(c.standingReachCm).isEqualTo(230.0)
    }

    @Test
    fun `three values that do not add up are flagged, not silently fixed`() {
        val c = JumpMetrics.complete(vertical = 70.0, standing = 230.0, total = 310.0)
        assertThat(c.inconsistent).isTrue()
        assertThat(c.derived).isNull()
        assertThat(c.totalReachCm).isEqualTo(310.0)
    }

    @Test
    fun `a total at or below standing reach is impossible`() {
        val c = JumpMetrics.complete(vertical = null, standing = 230.0, total = 225.0)
        assertThat(c.verticalJumpCm).isNull()
        assertThat(c.inconsistent).isTrue()
    }

    @Test
    fun `one value alone derives nothing`() {
        val c = JumpMetrics.complete(vertical = 70.0, standing = null, total = null)
        assertThat(c.derived).isNull()
        assertThat(c.totalReachCm).isNull()
    }

    @Test
    fun `overlay buckets by monday week and keeps empty weeks`() {
        val zone: ZoneId = ZoneOffset.UTC
        fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000
        val weeks = PerformanceOverlay.build(
            jumps = listOf(JumpPoint(at("2026-10-07"), 68.0), JumpPoint(at("2026-10-09"), 70.0)),
            sets = listOf(
                VolumeSample(at("2026-10-05"), 100.0, 5),
                VolumeSample(at("2026-10-11"), 100.0, 5), // Sunday: same week
                VolumeSample(at("2026-10-20"), 50.0, 10),
            ),
            zone = zone,
        )
        assertThat(weeks.map { it.weekStart.toString() })
            .containsExactly("2026-10-05", "2026-10-12", "2026-10-19").inOrder()
        assertThat(weeks[0].tonnageKg).isEqualTo(1_000.0)
        assertThat(weeks[0].workingSets).isEqualTo(2)
        assertThat(weeks[0].bestVerticalCm).isEqualTo(70.0)
        assertThat(weeks[1].tonnageKg).isEqualTo(0.0)
        assertThat(weeks[1].bestVerticalCm).isNull()
        assertThat(weeks[2].tonnageKg).isEqualTo(500.0)
    }

    @Test
    fun `overlay of nothing is empty`() {
        assertThat(PerformanceOverlay.build(emptyList(), emptyList(), ZoneOffset.UTC)).isEmpty()
    }
}
