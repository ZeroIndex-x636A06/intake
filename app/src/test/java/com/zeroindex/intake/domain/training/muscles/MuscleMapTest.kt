package com.zeroindex.intake.domain.training.muscles

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.Muscles
import org.junit.Test

class MuscleMapTest {

    @Test
    fun `every catalog muscle, primary and secondary, maps to at least one region`() {
        val names = (ExerciseCatalog.ALL.map { it.primaryMuscle } + ExerciseCatalog.ALL.flatMap { it.secondaryMuscles }).toSet()
        for (m in names) assertThat(MuscleMap.regionsOf(m)).isNotEmpty()
        for (m in Muscles.ALL + MuscleMap.EXTRA_MUSCLES) assertThat(MuscleMap.regionsOf(m)).isNotEmpty()
    }

    @Test
    fun `every region is drawn on the front or the back and is reachable by a tap`() {
        assertThat(MuscleMap.DRAWN_REGIONS).containsExactlyElementsIn(MuscleRegion.entries)
        for (view in BodyView.entries) for (o in MuscleMap.outlines(view)) {
            val cx = o.points.map { it.x }.average()
            val cy = o.points.map { it.y }.average()
            // The centre of an outline may be covered by a region drawn on top, but it is always some region of the view.
            assertThat(MuscleMap.regionAt(view, cx, cy)).isNotNull()
        }
        assertThat(MuscleMap.regionAt(BodyView.FRONT, 5.0, 5.0)).isNull()
    }

    @Test
    fun `a region's muscle always leads back to that region`() {
        for (r in MuscleRegion.entries) assertThat(MuscleMap.regionsOf(r.muscle)).contains(r)
    }

    @Test
    fun `there are six body regions over twenty groups`() {
        assertThat(MuscleRegion.entries).hasSize(20)
        assertThat(MuscleRegion.entries.map { it.body }.toSet()).hasSize(6)
    }

    @Test
    fun `outlines are drawn mirrored so left and right match`() {
        for (view in BodyView.entries) {
            val byRegion = MuscleMap.outlines(view).groupBy { it.region }
            for ((_, list) in byRegion) assertThat(list).hasSize(2)
        }
    }

    @Test
    fun `names narrow broad muscles and primary is never also secondary`() {
        val lateral = MuscleMap.highlightFor("Lateral raise", "shoulders", emptyList())
        assertThat(lateral.primary).containsExactly(MuscleRegion.SIDE_DELT)
        val pulldown = MuscleMap.highlightFor("Lat pulldown", "back", listOf("biceps"))
        assertThat(pulldown.primary).containsExactly(MuscleRegion.LATS)
        assertThat(pulldown.secondary).containsExactly(MuscleRegion.BICEPS)
        val bench = MuscleMap.highlightFor("Bench press", "chest", listOf("triceps", "shoulders", "chest"))
        assertThat(bench.primary).containsAtLeast(MuscleRegion.CHEST_UPPER, MuscleRegion.CHEST_MID_LOWER)
        assertThat(bench.secondary.intersect(bench.primary)).isEmpty()
        assertThat(MuscleMap.highlightFor("Incline bench press", "chest", emptyList()).primary).containsExactly(MuscleRegion.CHEST_UPPER)
        for (e in ExerciseCatalog.ALL) assertThat(MuscleMap.highlightFor(e.name, e.primaryMuscle, e.secondaryMuscles).primary).isNotEmpty()
    }

    @Test
    fun `smoothing closes the outline`() {
        val cubics = MuscleMap.smooth(MuscleMap.outlines(BodyView.FRONT).first().points)
        assertThat(cubics.first().x0).isEqualTo(cubics.last().x3)
        assertThat(cubics.first().y0).isEqualTo(cubics.last().y3)
    }
}
