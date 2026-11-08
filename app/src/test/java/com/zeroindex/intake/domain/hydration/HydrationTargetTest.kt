package com.zeroindex.intake.domain.hydration

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.VolumeUnit
import org.junit.Test

class HydrationTargetTest {

    @Test
    fun `base comes from bodyweight when nothing is set by hand`() {
        val target = HydrationTarget.compute(null, 84.0, dynamic = false, trainedToday = true, activeKcalNet = 600.0)
        // 84 kg x 35 ml = 2,940 ml, rounded to the nearest 50.
        assertThat(target.totalMl).isEqualTo(2_950.0)
        assertThat(target.reasons).isEmpty()
    }

    @Test
    fun `a manual base wins over bodyweight`() {
        val target = HydrationTarget.compute(3_000.0, 84.0, dynamic = false, trainedToday = false, activeKcalNet = null)
        assertThat(target.totalMl).isEqualTo(3_000.0)
    }

    @Test
    fun `falls back to a sane default before any weigh-in`() {
        val target = HydrationTarget.compute(null, null, dynamic = true, trainedToday = false, activeKcalNet = null)
        assertThat(target.totalMl).isEqualTo(HydrationTarget.FALLBACK_BASE_ML)
    }

    @Test
    fun `training day and activity each add and say why`() {
        val target = HydrationTarget.compute(3_000.0, null, dynamic = true, trainedToday = true, activeKcalNet = 420.0)
        assertThat(target.baseMl).isEqualTo(3_000.0)
        assertThat(target.reasons.map { it.label }).containsExactly("training day", "active calories").inOrder()
        assertThat(target.reasons.map { it.ml }).containsExactly(500.0, 400.0).inOrder()
        assertThat(target.totalMl).isEqualTo(3_900.0)
    }

    @Test
    fun `activity is capped so one long session cannot run away`() {
        val target = HydrationTarget.compute(2_500.0, null, dynamic = true, trainedToday = false, activeKcalNet = 4_000.0)
        assertThat(target.totalMl).isEqualTo(2_500.0 + HydrationTarget.ACTIVITY_CAP_ML)
    }

    @Test
    fun `negative or zero activity adds nothing`() {
        val target = HydrationTarget.compute(2_500.0, null, dynamic = true, trainedToday = false, activeKcalNet = -50.0)
        assertThat(target.reasons).isEmpty()
    }

    @Test
    fun `volume formats in both units`() {
        assertThat(VolumeUnit.MILLILITRES.format(1_850.0)).isEqualTo("1,850 ml")
        assertThat(VolumeUnit.US_FLUID_OUNCES.format(500.0)).isEqualTo("16.9 fl oz")
        assertThat(VolumeUnit.US_FLUID_OUNCES.format(VolumeUnit.US_FLUID_OUNCES.toMl(8.0))).isEqualTo("8 fl oz")
    }
}
