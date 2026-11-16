package com.zeroindex.intake.domain.training.muscles

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.MuscleWeights
import com.zeroindex.intake.domain.training.VolumeSet
import com.zeroindex.intake.domain.training.VolumeStatus
import com.zeroindex.intake.domain.training.WeeklyVolume
import org.junit.Test

/** The body diagram's per-muscle weighted volume. */
class MuscleVolumeTest {

    private val week = 1_000L..2_000L
    private val targets = BodyRegion.entries.associateWith { 10 }

    private fun set(id: String, name: String, primary: String, secondary: List<String> = emptyList(), at: Long = 1_500L, warmup: Boolean = false) =
        VolumeSet(id, name, primary, secondary, at, warmup)

    @Test
    fun `primary regions get full credit and helper regions half`() {
        val credit = MuscleWeights.muscleCredit("Bench press", "chest", listOf("triceps", "shoulders"))
        assertThat(credit[MuscleRegion.CHEST_UPPER]).isEqualTo(1.0)
        assertThat(credit[MuscleRegion.CHEST_MID_LOWER]).isEqualTo(1.0)
        assertThat(credit[MuscleRegion.TRICEPS]).isEqualTo(0.5)
        assertThat(credit[MuscleRegion.FRONT_DELT]).isEqualTo(0.5)
        assertThat(credit).doesNotContainKey(MuscleRegion.QUADS)
    }

    @Test
    fun `the exercise name narrows the main muscle`() {
        val lateral = MuscleWeights.muscleCredit("Lateral raise", "shoulders", emptyList())
        assertThat(lateral.keys).containsExactly(MuscleRegion.SIDE_DELT)
        val pulldown = MuscleWeights.muscleCredit("Lat pulldown", "back", listOf("biceps"))
        assertThat(pulldown[MuscleRegion.LATS]).isEqualTo(1.0)
        assertThat(pulldown).doesNotContainKey(MuscleRegion.MID_BACK)
        assertThat(pulldown[MuscleRegion.BICEPS]).isEqualTo(0.5)
    }

    @Test
    fun `a region is never both primary and helper`() {
        val credit = MuscleWeights.muscleCredit("Bench press", "chest", listOf("chest", "triceps"))
        assertThat(credit[MuscleRegion.CHEST_UPPER]).isEqualTo(1.0)
    }

    @Test
    fun `every region has a share and the share is a fraction`() {
        for (r in MuscleRegion.entries) assertThat(MuscleWeights.targetShare(r)).isIn(com.google.common.collect.Range.openClosed(0.0, 1.0))
    }

    @Test
    fun `computeMuscles returns all twenty regions with contributors and statuses`() {
        val sets = List(4) { set("bench", "Bench press", "chest", listOf("triceps")) } + set("sq", "Squat", "quads", listOf("glutes"))
        val out = WeeklyVolume.computeMuscles(sets, targets, week)
        assertThat(out.map { it.region }).containsExactlyElementsIn(MuscleRegion.entries).inOrder()
        val chest = out.first { it.region == MuscleRegion.CHEST_UPPER }
        assertThat(chest.sets).isEqualTo(4.0)
        assertThat(chest.contributors.map { it.exerciseName }).containsExactly("Bench press")
        assertThat(chest.contributors.single().loggedSets).isEqualTo(4)
        assertThat(chest.status).isEqualTo(VolumeStatus.UNDER)
        val tri = out.first { it.region == MuscleRegion.TRICEPS }
        assertThat(tri.sets).isEqualTo(2.0)
        val glutes = out.first { it.region == MuscleRegion.GLUTES }
        assertThat(glutes.sets).isEqualTo(0.5)
        assertThat(out.first { it.region == MuscleRegion.HAMSTRINGS }.contributors).isEmpty()
    }

    @Test
    fun `warm-ups and sets outside the week do not count`() {
        val sets = listOf(
            set("a", "Bench press", "chest", warmup = true),
            set("b", "Bench press", "chest", at = 500L),
            set("c", "Bench press", "chest"),
        )
        val chest = WeeklyVolume.computeMuscles(sets, targets, week).first { it.region == MuscleRegion.CHEST_UPPER }
        assertThat(chest.sets).isEqualTo(1.0)
    }

    @Test
    fun `small muscles carry half the body region's target, and a full week fills them`() {
        val none = WeeklyVolume.computeMuscles(emptyList(), targets, week)
        assertThat(none.first { it.region == MuscleRegion.CALVES }.target).isEqualTo(5)
        assertThat(none.first { it.region == MuscleRegion.QUADS }.target).isEqualTo(10)
        val sets = List(5) { set("cr", "Standing calf raise", "calves") }
        val calves = WeeklyVolume.computeMuscles(sets, targets, week).first { it.region == MuscleRegion.CALVES }
        assertThat(calves.status).isEqualTo(VolumeStatus.IN_RANGE)
    }

    @Test
    fun `every exercise in the catalog credits at least one region`() {
        for (e in com.zeroindex.intake.domain.training.generator.ExerciseCatalog.ALL) {
            assertThat(MuscleWeights.muscleCredit(e.name, e.primaryMuscle, e.secondaryMuscles)).isNotEmpty()
        }
    }

    @Test
    fun `the figure data is complete and well formed`() {
        for (view in BodyView.entries) {
            for (o in MuscleMap.outlines(view)) {
                assertThat(o.points.size).isAtLeast(5)
                for (p in o.points) {
                    assertThat(p.x).isIn(com.google.common.collect.Range.closed(0.0, MuscleMap.FIGURE_WIDTH))
                    assertThat(p.y).isIn(com.google.common.collect.Range.closed(0.0, MuscleMap.FIGURE_HEIGHT))
                }
            }
            assertThat(MuscleMap.lines(view)).isNotEmpty()
            for (l in MuscleMap.lines(view)) assertThat(MuscleMap.smoothOpen(l)).hasSize(l.size - 1)
        }
        assertThat(MuscleMap.silhouette().size).isAtLeast(7)
    }

    @Test
    fun `the right half of every outline mirrors the left`() {
        for (view in BodyView.entries) {
            for ((_, list) in MuscleMap.outlines(view).groupBy { it.region }) {
                val xs = list.flatMap { o -> o.points.map { it.x } }
                assertThat(xs.average()).isWithin(0.5).of(50.0)
            }
        }
    }
}
