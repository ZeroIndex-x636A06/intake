package com.zeroindex.intake.domain.charts

import com.zeroindex.intake.domain.health.OfferedWeighIn
import com.zeroindex.intake.domain.health.WeighInDedupe
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.tdee.WeightTrend
import com.zeroindex.intake.domain.time.localDateOf
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.WeightUnit
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Test

class ChartMathTest {

    // ----- scrubbing -----

    @Test fun nearestIndexPicksTheClosestPoint() {
        val xs = listOf(0.0, 10.0, 30.0, 100.0)
        assertEquals(0, ChartMath.nearestIndex(xs, -50.0))
        assertEquals(1, ChartMath.nearestIndex(xs, 12.0))
        assertEquals(2, ChartMath.nearestIndex(xs, 21.0))
        assertEquals(3, ChartMath.nearestIndex(xs, 5000.0))
    }

    @Test fun nearestIndexTieGoesToTheEarlierPointAndHandlesTinyLists() {
        assertEquals(0, ChartMath.nearestIndex(listOf(0.0, 10.0), 5.0))
        assertNull(ChartMath.nearestIndex(emptyList(), 1.0))
        assertEquals(0, ChartMath.nearestIndex(listOf(7.0), 99.0))
    }

    @Test fun nearestIndexOnDuplicateXStaysInRange() {
        val i = ChartMath.nearestIndex(listOf(1.0, 1.0, 1.0, 4.0), 1.2)!!
        assertTrue(i in 0..2)
    }

    // ----- gaps -----

    @Test fun runsBreakOnlyOnGapsLongerThanTheLimit() {
        val day = ChartMath.DAY_MS
        val xs = listOf(0.0, day, 2 * day, 2 * day + 20 * day, 2 * day + 21 * day)
        assertEquals(listOf(0..2, 3..4), ChartMath.runs(xs, ChartMath.GAP_DAYS * day))
        // Exactly the limit is not a gap.
        assertEquals(listOf(0..1), ChartMath.runs(listOf(0.0, 14 * day), 14 * day))
        assertEquals(emptyList(), ChartMath.runs(emptyList(), day))
    }

    // ----- axis labels -----

    @Test fun niceTicksAreRoundNumbersInsideTheRange() {
        val t = ChartMath.niceTicks(188.7, 201.2, 4)
        assertTrue(t.all { it in 188.7..201.2 }, "$t")
        assertTrue(t.size in 2..5, "$t")
        assertTrue(t.all { it % 5.0 == 0.0 }, "$t")
    }

    @Test fun niceTicksForSmallRangesHaveNoFloatingPointDrift() {
        val t = ChartMath.niceTicks(0.12, 0.58, 4)
        assertEquals(listOf(0.2, 0.4), t.take(2))
        assertEquals(1, ChartMath.tickDecimals(t))
    }

    @Test fun niceTicksDegenerateRangeIsEmpty() {
        assertEquals(emptyList(), ChartMath.niceTicks(5.0, 5.0))
        assertEquals(emptyList(), ChartMath.niceTicks(Double.NaN, 5.0))
    }

    @Test fun tickDecimals() {
        assertEquals(0, ChartMath.tickDecimals(listOf(190.0, 195.0, 200.0)))
        assertEquals(1, ChartMath.tickDecimals(listOf(1.5, 2.0, 2.5)))
        assertEquals(2, ChartMath.tickDecimals(listOf(0.05, 0.10)))
    }

    @Test fun sourceLabels() {
        assertEquals("Manual", ChartMath.sourceLabel(null))
        assertEquals("Manual", ChartMath.sourceLabel("MANUAL"))
        assertEquals("Health Connect", ChartMath.sourceLabel("HEALTH_CONNECT"))
        assertEquals("Csv Import", ChartMath.sourceLabel("CSV_IMPORT"))
    }

    // ----- units: kg stored, lb shown -----

    @Test fun kgToLbUsesTheExactFactor() {
        assertEquals(0.45359237, UnitConverter.KG_PER_LB, 0.0)
        assertEquals(2.2046226218, UnitConverter.kgToLb(1.0), 1e-9)
    }

    @Test fun aPoundWeightSurvivesTheRoundTripWithoutShowingDrift() {
        for (tenths in 1000..4000) {
            val lb = tenths / 10.0
            val kg = UnitConverter.lbToKg(lb)
            // Stored once, converted once, rounded once for display: the typed number comes back.
            assertEquals(lb, UnitConverter.round(WeightUnit.POUNDS.fromKg(kg), 1), 1e-9, "$lb lb")
        }
    }

    @Test fun roundingTheKilogramsFirstWouldShiftTheDisplayedPound() {
        // 195.0 lb is 88.4505 kg. Rounding to 0.1 kg first (88.5 kg) would show 195.1 lb.
        val stored = UnitConverter.lbToKg(195.0)
        val once = UnitConverter.round(WeightUnit.POUNDS.fromKg(stored), 1)
        val twice = UnitConverter.round(WeightUnit.POUNDS.fromKg(UnitConverter.round(stored, 1)), 1)
        assertEquals(195.0, once, 1e-9)
        assertTrue(abs(once - twice) > 0.0, "double rounding must differ here: $twice")
    }

    // ----- day bucketing and the time base -----

    @Test fun aLateEveningWeighInStaysOnItsLocalDay() {
        val la = ZoneId.of("America/Los_Angeles")
        val at = ZonedDateTime.of(2026, 3, 10, 22, 30, 0, 0, la).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 3, 10), localDateOf(at, la))
        assertEquals(LocalDate.of(2026, 3, 11), localDateOf(at, ZoneId.of("UTC")))
    }

    @Test fun trendXIsTheRealLocalDayStartNotUtcMidnight() {
        val ny = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 1, 15)
        val x = ChartMath.localDayStartMillis(day.toEpochDay(), ny)
        assertEquals(day.toEpochDay() * 86_400_000L + 5 * 3_600_000L, x)
        val spring = LocalDate.of(2026, 3, 8)
        assertEquals(spring, ChartMath.localDate(ChartMath.localDayStartMillis(spring.toEpochDay(), ny), ny))
    }

    // ----- the smoothed trend over sparse data -----

    @Test fun smoothByTimeMatchesTheDailyAverageWhenThereIsAWeighInEveryDay() {
        val pts = (0L until 30L).map { WeightPoint(it, 90.0 - it * 0.05 + if (it % 2 == 0L) 0.4 else -0.4) }
        WeightTrend.smooth(pts).zip(WeightTrend.smoothByTime(pts)).forEach { (x, y) ->
            assertEquals(x.trendKg, y.trendKg, 1e-12)
        }
    }

    @Test fun smoothByTimeFollowsAWeighInThatFollowsALongGap() {
        // 20 daily weigh-ins at 100 kg, 150 days of nothing, then 95.0 and 94.8.
        val old = (0L until 20L).map { WeightPoint(it, 100.0) }
        val recent = listOf(WeightPoint(170, 95.0), WeightPoint(171, 94.8))
        val perSample = WeightTrend.smooth(old + recent).last().trendKg
        val byTime = WeightTrend.smoothByTime(old + recent).last().trendKg
        // The per-sample average barely moved: the lag the owner saw.
        assertTrue(perSample > 99.0, "per-sample $perSample")
        assertEquals(94.98, byTime, 0.01)
    }

    @Test fun smoothByTimeEdgeCases() {
        assertEquals(emptyList(), WeightTrend.smoothByTime(emptyList()))
        assertEquals(88.0, WeightTrend.smoothByTime(listOf(WeightPoint(5, 88.0))).single().trendKg, 0.0)
        val t = WeightTrend.smoothByTime(listOf(WeightPoint(1, 80.0), WeightPoint(1, 82.0)))
        assertEquals(81.0, t.single().trendKg, 1e-12)
    }

    // ----- two apps writing the same weigh-in -----

    @Test fun twoAppsReportingTheSameWeighInImportOnce_theEarlierReadingWins() {
        val offered = listOf(OfferedWeighIn("scale", 1_000_000L + 40_000), OfferedWeighIn("samsung", 1_000_000L))
        val out = WeighInDedupe.toImport(offered, emptyList()) { "hc-weight-$it" }
        assertEquals(listOf("samsung"), out.map { it.id })
    }

    @Test fun readingsMoreThanTheWindowApartAreBothKept() {
        val offered = listOf(OfferedWeighIn("a", 0L), OfferedWeighIn("b", WeighInDedupe.MATCH_WINDOW_MS + 1))
        assertEquals(2, WeighInDedupe.toImport(offered, emptyList()) { it }.size)
    }
}

private fun <T> assertEquals(expected: T, actual: T) = org.junit.Assert.assertEquals(expected, actual)
private fun assertEquals(expected: Double, actual: Double, delta: Double, message: String? = null) =
    org.junit.Assert.assertEquals(message, expected, actual, delta)
private fun assertTrue(condition: Boolean, message: String? = null) = org.junit.Assert.assertTrue(message, condition)
private fun assertNull(value: Any?) = org.junit.Assert.assertNull(value)
