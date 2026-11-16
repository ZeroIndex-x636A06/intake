package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class WorkoutLogicTest {

    private val utc = ZoneId.of("UTC")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, utc).toInstant().toEpochMilli()

    // --- weekly volume --------------------------------------------------------------------------------------

    private fun set(id: String, primary: String, secondary: List<String>, time: Long, warmup: Boolean = false) =
        VolumeSet(id, id, primary, secondary, time, warmup)

    @Test
    fun `weeks start on Monday`() {
        // 2026-11-10 is a Tuesday.
        assertThat(WeeklyVolume.weekStart(at(2026, 11, 10), utc)).isEqualTo(at(2026, 11, 9, 0))
        assertThat(WeeklyVolume.weekStart(at(2026, 11, 9, 0), utc)).isEqualTo(at(2026, 11, 9, 0))
        // Sunday still belongs to the week that began on the Monday before.
        assertThat(WeeklyVolume.weekStart(at(2026, 11, 15, 23), utc)).isEqualTo(at(2026, 11, 9, 0))
    }

    @Test
    fun `week ranges are calendar weeks`() {
        val r = WeeklyVolume.weekRange(at(2026, 11, 10), utc)
        assertThat(r.first).isEqualTo(at(2026, 11, 9, 0))
        assertThat(r.last + 1).isEqualTo(at(2026, 11, 16, 0))
        val last = WeeklyVolume.weekRange(at(2026, 11, 10), utc, weeksAgo = 1)
        assertThat(last.first).isEqualTo(at(2026, 11, 2, 0))
    }

    @Test
    fun `a set at the very start of the next week is not in this week`() {
        val range = WeeklyVolume.weekRange(at(2026, 11, 10), utc)
        val sets = listOf(set("bench", "chest", emptyList(), at(2026, 11, 16, 0)), set("bench", "chest", emptyList(), at(2026, 11, 15, 23)))
        val chest = WeeklyVolume.compute(sets, emptyMap(), range).first { it.region == BodyRegion.CHEST }
        assertThat(chest.sets).isEqualTo(1.0)
    }

    @Test
    fun `primary counts one and secondary counts a half`() {
        val range = WeeklyVolume.weekRange(at(2026, 11, 10), utc)
        val sets = List(4) { set("bench", "chest", listOf("triceps", "shoulders"), at(2026, 11, 10)) }
        val v = WeeklyVolume.compute(sets, emptyMap(), range).associateBy { it.region }
        assertThat(v.getValue(BodyRegion.CHEST).sets).isEqualTo(4.0)
        assertThat(v.getValue(BodyRegion.ARMS).sets).isEqualTo(2.0)
        assertThat(v.getValue(BodyRegion.SHOULDERS).sets).isEqualTo(2.0)
        assertThat(v.getValue(BodyRegion.LEGS).sets).isEqualTo(0.0)
    }

    @Test
    fun `two helpers in one region count once`() {
        val credit = MuscleWeights.regionCredit("chest", listOf("biceps", "triceps"))
        assertThat(credit.getValue(BodyRegion.ARMS)).isEqualTo(0.5)
        // A helper in the same region as the main muscle adds nothing.
        assertThat(MuscleWeights.regionCredit("quads", listOf("glutes", "hamstrings")).getValue(BodyRegion.LEGS)).isEqualTo(1.0)
    }

    @Test
    fun `warm-ups never count`() {
        val range = WeeklyVolume.weekRange(at(2026, 11, 10), utc)
        val v = WeeklyVolume.compute(listOf(set("squat", "quads", emptyList(), at(2026, 11, 10), warmup = true)), emptyMap(), range)
        assertThat(v.all { it.sets == 0.0 }).isTrue()
    }

    @Test
    fun `status against the target band`() {
        // Target 12: in range is 9 to 18.
        assertThat(WeeklyVolume.band(12)).isEqualTo(9 to 18)
        assertThat(WeeklyVolume.status(8.5, 12)).isEqualTo(VolumeStatus.UNDER)
        assertThat(WeeklyVolume.status(9.0, 12)).isEqualTo(VolumeStatus.IN_RANGE)
        assertThat(WeeklyVolume.status(18.0, 12)).isEqualTo(VolumeStatus.IN_RANGE)
        assertThat(WeeklyVolume.status(18.5, 12)).isEqualTo(VolumeStatus.OVER)
    }

    @Test
    fun `contributors are listed with their effective sets`() {
        val range = WeeklyVolume.weekRange(at(2026, 11, 10), utc)
        val sets = List(3) { set("Bench press", "chest", listOf("triceps"), at(2026, 11, 10)) } +
            List(2) { set("Dips", "triceps", listOf("chest"), at(2026, 11, 11)) }
        val chest = WeeklyVolume.compute(sets, mapOf(BodyRegion.CHEST to 12), range).first { it.region == BodyRegion.CHEST }
        assertThat(chest.contributors.map { it.exerciseName }).containsExactly("Bench press", "Dips").inOrder()
        assertThat(chest.contributors.map { it.effectiveSets }).containsExactly(3.0, 1.0).inOrder()
        assertThat(chest.contributors.first().loggedSets).isEqualTo(3)
        assertThat(chest.target).isEqualTo(12)
    }

    @Test
    fun `the hint names the lightest region`() {
        val range = WeeklyVolume.weekRange(at(2026, 11, 10), utc)
        val sets = List(12) { set("Bench", "chest", emptyList(), at(2026, 11, 10)) }
        val v = WeeklyVolume.compute(sets, emptyMap(), range)
        assertThat(WeeklyVolume.hint(v)).startsWith("Light on")
        assertThat(WeeklyVolume.hint(WeeklyVolume.compute(emptyList(), emptyMap(), range))).isNull()
    }

    // --- plates ---------------------------------------------------------------------------------------------

    @Test
    fun `plate line`() {
        assertThat(PlateLine.text(listOf(2.5, 45.0, 25.0))).isEqualTo("Per side: 45 + 25 + 2.5")
        assertThat(PlateLine.text(emptyList())).contains("just the bar")
        assertThat(PlateLine.text(listOf(20.0, 10.0)) { it * 2.20462 }).isEqualTo("Per side: 44 + 22")
    }

    // --- previous set ---------------------------------------------------------------------------------------

    @Test
    fun `previous is the same set of last time`() {
        val last = listOf(SetResult(135.0, 8), SetResult(135.0, 8), SetResult(135.0, 7), SetResult(130.0, 8))
        assertThat(PreviousSets.forSet(last, 2)).isEqualTo(SetResult(135.0, 7))
        assertThat(PreviousSets.forSet(last, 3)).isEqualTo(SetResult(130.0, 8))
    }

    @Test
    fun `previous falls back to the last set when last time had fewer`() {
        val last = listOf(SetResult(100.0, 10), SetResult(100.0, 9))
        assertThat(PreviousSets.forSet(last, 4)).isEqualTo(SetResult(100.0, 9))
        assertThat(PreviousSets.forSet(emptyList(), 0)).isNull()
    }

    // --- PR detection ---------------------------------------------------------------------------------------

    @Test
    fun `a heavier set is a new estimated max`() {
        val history = listOf(SetResult(100.0, 5), SetResult(100.0, 5))
        assertThat(PrDetector.check(105.0, 5, history)).isEqualTo(PrKind.ESTIMATED_MAX)
    }

    @Test
    fun `more reps at the same weight is a rep record`() {
        // 100 x 6 and 100 x 5 have different estimates, so 100 x 6 is also an estimated max: use a lighter prior best.
        val history = listOf(SetResult(100.0, 8), SetResult(80.0, 6))
        assertThat(PrDetector.check(80.0, 7, history)).isEqualTo(PrKind.REP_RECORD)
    }

    @Test
    fun `equalling the best is not a record`() {
        val history = listOf(SetResult(100.0, 5))
        assertThat(PrDetector.check(100.0, 5, history)).isEqualTo(PrKind.NONE)
        assertThat(PrDetector.check(90.0, 5, history)).isEqualTo(PrKind.NONE)
    }

    @Test
    fun `the first time ever is not a record`() {
        assertThat(PrDetector.check(100.0, 5, emptyList())).isEqualTo(PrKind.NONE)
        assertThat(PrDetector.check(0.0, 5, listOf(SetResult(50.0, 5)))).isEqualTo(PrKind.NONE)
    }

    // --- swap ranking ---------------------------------------------------------------------------------------

    private fun ex(id: String, primary: String, sec: List<String> = emptyList(), eq: String = "barbell", used: Long? = null) =
        SwapCandidate(id, id, primary, sec, eq, used)

    @Test
    fun `same main muscle ranks above a helper match`() {
        val current = ex("bench", "chest", listOf("triceps", "shoulders"))
        val ranked = SwapRanking.rank(
            current,
            listOf(ex("dip", "triceps", listOf("chest")), ex("fly", "chest", eq = "cable"), ex("curl", "biceps")),
            owned = setOf("barbell", "cable", "dumbbell"),
        )
        assertThat(ranked.map { it.candidate.id }).containsExactly("fly", "dip").inOrder()
    }

    @Test
    fun `owned equipment ranks first within equal similarity`() {
        val current = ex("bench", "chest", listOf("triceps"))
        val ranked = SwapRanking.rank(
            current,
            listOf(ex("a-machine", "chest", listOf("triceps"), eq = "machine"), ex("b-dumbbell", "chest", listOf("triceps"), eq = "dumbbell")),
            owned = setOf("dumbbell"),
        )
        assertThat(ranked.map { it.candidate.id }).containsExactly("b-dumbbell", "a-machine").inOrder()
        assertThat(ranked[1].ownsEquipment).isFalse()
    }

    @Test
    fun `most recently used breaks a tie and the current exercise is never offered`() {
        val current = ex("bench", "chest")
        val ranked = SwapRanking.rank(
            current,
            listOf(ex("bench", "chest"), ex("old", "chest", used = 5), ex("recent", "chest", used = 50)),
            owned = setOf("barbell"),
        )
        assertThat(ranked.map { it.candidate.id }).containsExactly("recent", "old").inOrder()
    }

    @Test
    fun `no shared muscle means no candidate and bodyweight is always owned`() {
        val ranked = SwapRanking.rank(ex("squat", "quads"), listOf(ex("curl", "biceps"), ex("pushup", "quads", eq = "bodyweight")), owned = emptySet())
        assertThat(ranked.map { it.candidate.id }).containsExactly("pushup")
        assertThat(ranked.single().ownsEquipment).isTrue()
        assertThat(SwapRanking.owns("", emptySet())).isTrue()
    }

    @Test
    fun `swap weight uses history first`() {
        assertThat(SwapWeight.carryOver(ownLastKg = 30.0, replacedKg = 100.0, fromEquipment = "barbell", toEquipment = "dumbbell")).isEqualTo(30.0)
    }

    @Test
    fun `swap weight estimates conservatively and rounds down to the plate step`() {
        // 100 kg barbell to dumbbells: 100 x 0.4 x 0.85 = 34 -> 32.5.
        assertThat(SwapWeight.carryOver(null, 100.0, "barbell", "dumbbell")).isEqualTo(32.5)
        assertThat(SwapWeight.carryOver(null, null, "barbell", "dumbbell")).isNull()
        assertThat(SwapWeight.carryOver(null, 50.0, "barbell", "bodyweight")).isNull()
    }

    // --- session estimate -----------------------------------------------------------------------------------

    @Test
    fun `session estimate is rounded to five minutes`() {
        val slots = listOf(SessionEstimate.Slot(4, 120), SessionEstimate.Slot(3, 90), SessionEstimate.Slot(3, 90))
        // 4 x 160 + 2 x 3 x 130 = 1420 s = 24 min, plus 5 = 29 -> 30.
        assertThat(SessionEstimate.minutes(slots)).isEqualTo(30)
        assertThat(SessionEstimate.minutes(emptyList())).isEqualTo(0)
    }

    // --- summary --------------------------------------------------------------------------------------------

    private fun s(id: String, kg: Double, reps: Int, primary: String = "chest", sec: List<String> = listOf("triceps"), warmup: Boolean = false) =
        SummarySet(id, id, primary, sec, kg, reps, warmup)

    @Test
    fun `summary totals ignore warm-ups`() {
        val sum = SessionSummaries.build(
            listOf(s("Bench", 40.0, 10, warmup = true), s("Bench", 100.0, 5), s("Bench", 100.0, 5)),
            emptyMap(), 0, 45 * 60_000L,
        )
        assertThat(sum.workingSets).isEqualTo(2)
        assertThat(sum.volumeKg).isEqualTo(1000.0)
        assertThat(sum.totalReps).isEqualTo(10)
        assertThat(sum.durationMin).isEqualTo(45)
        assertThat(sum.regionSets.getValue(BodyRegion.CHEST)).isEqualTo(2.0)
        assertThat(sum.regionSets.getValue(BodyRegion.ARMS)).isEqualTo(1.0)
    }

    @Test
    fun `a new estimated max is reported with its delta`() {
        val sum = SessionSummaries.build(
            listOf(s("Bench", 105.0, 5)),
            mapOf("Bench" to listOf(SetResult(100.0, 5))), 0, 1,
        )
        val record = sum.records.single()
        assertThat(record.kind).isEqualTo(PrKind.ESTIMATED_MAX)
        assertThat(record.value).isGreaterThan(record.previous!!)
    }

    @Test
    fun `a rep record at a repeated weight`() {
        val sum = SessionSummaries.build(
            listOf(s("Curl", 20.0, 11, "biceps", emptyList())),
            mapOf("Curl" to listOf(SetResult(20.0, 12), SetResult(25.0, 6))), 0, 1,
        )
        // 20 x 11 does not beat 20 x 12, and 25 x 6 estimates higher, so there is no record.
        assertThat(sum.records).isEmpty()
        val better = SessionSummaries.build(
            listOf(s("Curl", 20.0, 13, "biceps", emptyList())),
            mapOf("Curl" to listOf(SetResult(20.0, 12), SetResult(25.0, 9))), 0, 1,
        )
        assertThat(better.records.single().kind).isEqualTo(PrKind.REP_RECORD)
    }

    @Test
    fun `first time ever is not a record`() {
        val sum = SessionSummaries.build(listOf(s("Bench", 100.0, 5)), emptyMap(), 0, 1)
        assertThat(sum.records).isEmpty()
    }

    @Test
    fun `next time change is reported only when the weight moves`() {
        assertThat(SessionSummaries.nextTime("Bench press", 140.0, 142.5, 8, "hit the top")).isNotNull()
        assertThat(SessionSummaries.nextTime("Bench press", 140.0, 140.0, 8, "same")).isNull()
        assertThat(SessionSummaries.nextTime("Plank", 0.0, 0.0, 1, "")).isNull()
    }
}
