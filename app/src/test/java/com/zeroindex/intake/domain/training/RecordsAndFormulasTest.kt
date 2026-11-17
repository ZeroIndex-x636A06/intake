package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.log.StatSet
import com.zeroindex.intake.domain.training.log.TrainingStats
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

/** WP-6: the 1RM formulas and their one setting, the four record kinds, bodyweight sets, and the calculators. */
class RecordsAndFormulasTest {

    @After
    fun resetFormula() {
        OneRepMax.formula = OneRepMaxFormula.DEFAULT
    }

    // --- formulas ----------------------------------------------------------------------------------------------

    @Test
    fun `each formula matches its published definition`() {
        // 100 x 5
        assertThat(OneRepMaxFormula.EPLEY.estimate(100.0, 5)).isWithin(0.001).of(100 * (1 + 5 / 30.0))
        assertThat(OneRepMaxFormula.BRZYCKI.estimate(100.0, 5)).isWithin(0.001).of(112.5)
        assertThat(OneRepMaxFormula.LOMBARDI.estimate(100.0, 5)).isWithin(0.001).of(100 * Math.pow(5.0, 0.10))
        val mean = (OneRepMaxFormula.EPLEY.estimate(100.0, 5) + 112.5 + OneRepMaxFormula.LOMBARDI.estimate(100.0, 5)) / 3
        assertThat(OneRepMaxFormula.AVERAGE.estimate(100.0, 5)).isWithin(0.001).of(mean)
    }

    @Test
    fun `a single is the weight itself in every formula`() {
        OneRepMaxFormula.entries.forEach { assertThat(it.estimate(140.0, 1)).isEqualTo(140.0) }
    }

    @Test
    fun `zero reps is refused in every formula`() {
        OneRepMaxFormula.entries.forEach { f -> assertThrows(IllegalArgumentException::class.java) { f.estimate(100.0, 0) } }
    }

    @Test
    fun `brzycki stays finite for very high reps`() {
        val v = OneRepMaxFormula.BRZYCKI.estimate(20.0, 50)
        assertThat(v.isFinite()).isTrue()
        assertThat(v).isEqualTo(OneRepMaxFormula.BRZYCKI.estimate(20.0, 36))
    }

    @Test
    fun `estimates never go down as the reps go up`() {
        OneRepMaxFormula.entries.forEach { f ->
            var last = 0.0
            for (reps in 1..30) {
                val e = f.estimate(80.0, reps)
                assertThat(e).isAtLeast(last)
                last = e
            }
        }
    }

    @Test
    fun `an unknown or missing id falls back to epley`() {
        assertThat(OneRepMaxFormula.byId(null)).isEqualTo(OneRepMaxFormula.EPLEY)
        assertThat(OneRepMaxFormula.byId("nonsense")).isEqualTo(OneRepMaxFormula.EPLEY)
        assertThat(OneRepMaxFormula.byId("brzycki")).isEqualTo(OneRepMaxFormula.BRZYCKI)
        assertThat(OneRepMaxFormula.entries.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `the setting changes every estimate that goes through OneRepMax`() {
        val sets = listOf(SetResult(100.0, 5))
        assertThat(OneRepMax.best(sets)).isWithin(0.001).of(116.667)
        OneRepMax.formula = OneRepMaxFormula.BRZYCKI
        assertThat(OneRepMax.best(sets)).isWithin(0.001).of(112.5)
        assertThat(OneRepMax.formulaText).contains("Brzycki")
        assertThat(OneRepMax.estimate(100.0, 5)).isWithin(0.001).of(112.5)
    }

    @Test
    fun `the stats and the records follow the formula`() {
        OneRepMax.formula = OneRepMaxFormula.BRZYCKI
        val data = TrainingStats.build(listOf(stat("a", 1, "bench", 100.0, 5)))
        assertThat(data.lifts.single().estimatedMaxKg).isWithin(0.001).of(112.5)
        assertThat(data.records.single().records.bestEstimatedMax!!.estimatedKg).isWithin(0.001).of(112.5)
    }

    // --- PrDetector: set-level records ---------------------------------------------------------------------------

    @Test
    fun `a heavier set that is not a better estimate is still the heaviest weight`() {
        val history = listOf(SetResult(100.0, 8))
        // 110 x 1 estimates 110, below 100 x 8 (126.7), but it is the heaviest weight.
        assertThat(PrDetector.kinds(110.0, 1, history)).containsExactly(PrKind.HEAVIEST_WEIGHT)
        assertThat(PrDetector.check(110.0, 1, history)).isEqualTo(PrKind.HEAVIEST_WEIGHT)
    }

    @Test
    fun `one set can earn several kinds and check picks the first`() {
        val history = listOf(SetResult(100.0, 5), SetResult(80.0, 5))
        assertThat(PrDetector.kinds(105.0, 5, history)).containsExactly(PrKind.ESTIMATED_MAX, PrKind.HEAVIEST_WEIGHT).inOrder()
        assertThat(PrDetector.check(105.0, 5, history)).isEqualTo(PrKind.ESTIMATED_MAX)
    }

    @Test
    fun `equal weight and reps earn nothing`() {
        assertThat(PrDetector.kinds(100.0, 5, listOf(SetResult(100.0, 5)))).isEmpty()
    }

    @Test
    fun `the formula changes which sets are records`() {
        // History 100 x 5: Epley 116.7, Lombardi 100 x 5^0.1 = 117.5. A new 90 x 8 is Epley 114 (no), Lombardi 90 x 8^0.1 = 110.8 (no);
        // use 80 x 15 instead: Epley 120 (record), Lombardi 80 x 15^0.1 = 104.8 (not a record).
        val history = listOf(SetResult(100.0, 5))
        OneRepMax.formula = OneRepMaxFormula.EPLEY
        assertThat(PrDetector.kinds(80.0, 15, history)).contains(PrKind.ESTIMATED_MAX)
        OneRepMax.formula = OneRepMaxFormula.LOMBARDI
        assertThat(PrDetector.kinds(80.0, 15, history)).doesNotContain(PrKind.ESTIMATED_MAX)
    }

    // --- bodyweight ------------------------------------------------------------------------------------------------

    @Test
    fun `a bodyweight set earns only the rep record`() {
        val history = listOf(SetResult(0.0, 10), SetResult(0.0, 8))
        assertThat(PrDetector.kinds(0.0, 12, history)).containsExactly(PrKind.REP_RECORD)
        assertThat(PrDetector.kinds(0.0, 10, history)).isEmpty()
        assertThat(PrDetector.kinds(0.0, 6, history)).isEmpty()
    }

    @Test
    fun `the first bodyweight set ever is not a record`() {
        assertThat(PrDetector.kinds(0.0, 12, emptyList())).isEmpty()
    }

    @Test
    fun `adding weight to a bodyweight exercise is not a record against bodyweight history alone`() {
        assertThat(PrDetector.kinds(10.0, 5, listOf(SetResult(0.0, 12)))).isEmpty()
        // The next added-weight set is measured against the first one.
        assertThat(PrDetector.kinds(12.5, 5, listOf(SetResult(0.0, 12), SetResult(10.0, 5)))).containsExactly(PrKind.ESTIMATED_MAX, PrKind.HEAVIEST_WEIGHT).inOrder()
    }

    // --- the board: records per exercise ----------------------------------------------------------------------------

    private fun r(w: Double, reps: Int, session: String?) = SetResult(w, reps, sessionId = session)

    @Test
    fun `the board has all four records`() {
        val board = ExerciseRecordsCalc.compute(
            listOf(
                r(100.0, 5, "a"), r(100.0, 5, "a"), r(100.0, 4, "a"),     // volume 1400
                r(110.0, 3, "b"), r(90.0, 10, "b"),                        // volume 1230
                r(100.0, 6, "c"),                                          // volume 600
            ),
        )
        assertThat(board.heaviest).isEqualTo(WeightReps(110.0, 3))
        // Epley: 110 x 3 = 121 beats 90 x 10 = 120.
        assertThat(board.bestEstimatedMax!!.weightKg).isEqualTo(110.0)
        assertThat(board.bestVolume).isEqualTo(BestVolume(1400.0, 3))
    }

    @Test
    fun `the best estimated max is the highest estimate`() {
        val board = ExerciseRecordsCalc.compute(listOf(r(110.0, 3, "b"), r(90.0, 10, "b"), r(100.0, 6, "c")))
        // Epley: 110x3 = 121, 90x10 = 120, 100x6 = 120.
        assertThat(board.bestEstimatedMax).isEqualTo(BestEstimate(OneRepMax.estimate(110.0, 3), 110.0, 3))
    }

    @Test
    fun `best session volume sums a whole session`() {
        val board = ExerciseRecordsCalc.compute(
            listOf(r(100.0, 5, "a"), r(100.0, 5, "a"), r(100.0, 4, "a"), r(110.0, 3, "b"), r(90.0, 10, "b")),
        )
        assertThat(board.bestVolume).isEqualTo(BestVolume(1400.0, 3))
    }

    @Test
    fun `most reps are kept per weight, lightest first`() {
        val board = ExerciseRecordsCalc.compute(listOf(r(100.0, 5, "a"), r(100.0, 7, "b"), r(60.0, 15, "a"), r(0.0, 9, "a")))
        assertThat(board.repRecords).containsExactly(WeightReps(0.0, 9), WeightReps(60.0, 15), WeightReps(100.0, 7)).inOrder()
        assertThat(board.bodyweightReps).isEqualTo(9)
    }

    @Test
    fun `a bodyweight exercise has a rep record and nothing else`() {
        val board = ExerciseRecordsCalc.compute(listOf(r(0.0, 10, "a"), r(0.0, 12, "b")))
        assertThat(board.heaviest).isNull()
        assertThat(board.bestEstimatedMax).isNull()
        assertThat(board.bestVolume).isNull()
        assertThat(board.bodyweightReps).isEqualTo(12)
        assertThat(board.isEmpty).isFalse()
    }

    @Test
    fun `no sets and invalid sets give an empty board`() {
        assertThat(ExerciseRecordsCalc.compute(emptyList()).isEmpty).isTrue()
        assertThat(ExerciseRecordsCalc.compute(listOf(r(50.0, 0, "a"), r(-5.0, 5, "a"))).isEmpty).isTrue()
    }

    @Test
    fun `sets of an unknown session count as one session for the volume`() {
        val board = ExerciseRecordsCalc.compute(listOf(r(50.0, 10, null), r(50.0, 10, null)))
        assertThat(board.bestVolume!!.volumeKg).isEqualTo(1000.0)
    }

    // --- the fourth record: session volume ---------------------------------------------------------------------------

    @Test
    fun `a session with more volume than any earlier one is a volume record`() {
        val history = listOf(r(100.0, 5, "a"), r(100.0, 5, "a"))              // 1000
        val rec = ExerciseRecordsCalc.volumeRecord(history, listOf(SetResult(80.0, 8), SetResult(80.0, 8))) // 1280
        assertThat(rec).isEqualTo(VolumeRecord(1280.0, 1000.0))
        assertThat(ExerciseRecordsCalc.volumeRecord(history, listOf(SetResult(100.0, 5), SetResult(100.0, 5)))).isNull()
    }

    @Test
    fun `the first session ever and bodyweight work have no volume record`() {
        assertThat(ExerciseRecordsCalc.volumeRecord(emptyList(), listOf(SetResult(100.0, 5)))).isNull()
        assertThat(ExerciseRecordsCalc.volumeRecord(listOf(r(0.0, 10, "a")), listOf(SetResult(0.0, 20)))).isNull()
    }

    @Test
    fun `volume is compared against the single best earlier session, not the sum`() {
        val history = listOf(r(100.0, 5, "a"), r(100.0, 5, "b"))               // 500 each
        assertThat(ExerciseRecordsCalc.volumeRecord(history, listOf(SetResult(100.0, 8)))).isNotNull()   // 800 > 500
        assertThat(ExerciseRecordsCalc.volumeRecord(history, listOf(SetResult(100.0, 4)))).isNull()
    }

    // --- the session summary ------------------------------------------------------------------------------------------

    private fun sum(id: String, kg: Double, reps: Int) = SummarySet(id, id, "chest", emptyList(), kg, reps)

    @Test
    fun `the summary reports a heaviest weight and a session volume record`() {
        val summary = SessionSummaries.build(
            listOf(sum("Row", 60.0, 10), sum("Row", 60.0, 10), sum("Row", 60.0, 10), sum("Row", 62.5, 3)),
            mapOf("Row" to listOf(r(60.0, 10, "a"), r(60.0, 9, "a"), r(60.0, 8, "a"))), 0, 1,
        )
        val kinds = summary.records.map { it.kind }
        assertThat(kinds).contains(PrKind.HEAVIEST_WEIGHT)
        assertThat(kinds).contains(PrKind.BEST_VOLUME)
        val heavy = summary.records.first { it.kind == PrKind.HEAVIEST_WEIGHT }
        assertThat(heavy.value).isEqualTo(62.5)
        assertThat(heavy.previous).isEqualTo(60.0)
        val vol = summary.records.first { it.kind == PrKind.BEST_VOLUME }
        assertThat(vol.value).isEqualTo(60.0 * 10 * 3 + 62.5 * 3)
        assertThat(vol.previous).isEqualTo(60.0 * 27)
    }

    @Test
    fun `the summary gives a bodyweight rep record and no weight records`() {
        val summary = SessionSummaries.build(
            listOf(sum("Pull-up", 0.0, 12)),
            mapOf("Pull-up" to listOf(r(0.0, 10, "a"), r(0.0, 9, "a"))), 0, 1,
        )
        val rec = summary.records.single()
        assertThat(rec.kind).isEqualTo(PrKind.REP_RECORD)
        assertThat(rec.value).isEqualTo(12.0)
        assertThat(rec.previous).isEqualTo(10.0)
        assertThat(rec.weightKg).isEqualTo(0.0)
    }

    @Test
    fun `an equal bodyweight session is not a record`() {
        val summary = SessionSummaries.build(listOf(sum("Pull-up", 0.0, 10)), mapOf("Pull-up" to listOf(r(0.0, 10, "a"))), 0, 1)
        assertThat(summary.records).isEmpty()
    }

    @Test
    fun `the summary follows the formula`() {
        // 100 x 5 before; 80 x 10 now. Epley: 106.7 vs 116.7, no record. Lombardi: 80 x 10^0.1 = 100.7 vs 117.5, no record.
        // Use a case where the formulas disagree: 70 x 12 vs 100 x 5.
        val before = mapOf("Bench" to listOf(r(100.0, 5, "a")))
        val set = listOf(sum("Bench", 70.0, 12))
        OneRepMax.formula = OneRepMaxFormula.EPLEY
        val epley = SessionSummaries.build(set, before, 0, 1).records.any { it.kind == PrKind.ESTIMATED_MAX }
        OneRepMax.formula = OneRepMaxFormula.BRZYCKI
        val brzycki = SessionSummaries.build(set, before, 0, 1).records.any { it.kind == PrKind.ESTIMATED_MAX }
        // Epley: 70 x (1 + 12/30) = 98 < 116.7. Brzycki: 70 x 36/25 = 100.8 < 112.5. Neither is a record.
        assertThat(epley).isFalse()
        assertThat(brzycki).isFalse()
    }

    // --- the Training stats screen ------------------------------------------------------------------------------------

    private fun stat(session: String, day: Long, ex: String, kg: Double, reps: Int, idx: Int = 0) =
        StatSet(session, day * 86_400_000L, ex, ex.uppercase(), idx, kg, reps, false, day * 86_400_000L + idx)

    @Test
    fun `the stats carry the records of each exercise, bodyweight ones included`() {
        val data = TrainingStats.build(
            listOf(
                stat("a", 1, "bench", 100.0, 5), stat("b", 8, "bench", 105.0, 5),
                stat("a", 1, "pullup", 0.0, 8), stat("b", 8, "pullup", 0.0, 10),
            ),
        )
        assertThat(data.lifts.map { it.exerciseId }).containsExactly("bench")
        val byId = data.records.associateBy { it.exerciseId }
        assertThat(byId.getValue("bench").records.heaviest).isEqualTo(WeightReps(105.0, 5))
        assertThat(byId.getValue("pullup").records.bodyweightReps).isEqualTo(10)
        assertThat(data.isEmpty).isFalse()
        // The bodyweight rep record shows in the recent records too.
        assertThat(data.recentPrs.any { it.exerciseName == "PULLUP" && it.kind == PrKind.REP_RECORD }).isTrue()
    }

    @Test
    fun `a log with only bodyweight sets is not empty`() {
        val data = TrainingStats.build(listOf(stat("a", 1, "pushup", 0.0, 20)))
        assertThat(data.lifts).isEmpty()
        assertThat(data.records.single().records.bodyweightReps).isEqualTo(20)
        assertThat(data.isEmpty).isFalse()
    }

    @Test
    fun `warm-ups never make a record`() {
        val warm = StatSet("a", 0, "bench", "BENCH", 0, 200.0, 1, true, 0)
        assertThat(TrainingStats.build(listOf(warm)).records).isEmpty()
    }

    // --- words ------------------------------------------------------------------------------------------------------

    @Test
    fun `record lines are written in the person's unit`() {
        val board = ExerciseRecordsCalc.compute(listOf(r(100.0, 5, "a"), r(100.0, 5, "a")))
        val kg = RecordText.lines(board, WeightUnit.KILOGRAMS)
        assertThat(kg.map { it.title }).containsExactly("Heaviest weight", "Best estimated 1RM", "Best session volume").inOrder()
        assertThat(kg[0].value).isEqualTo("100 kg")
        assertThat(kg[2].value).isEqualTo("1,000 kg")
        val lb = RecordText.lines(board, WeightUnit.POUNDS)
        assertThat(lb[0].value).isEqualTo("220.5 lb")
    }

    @Test
    fun `bodyweight reads as such in the reps chips`() {
        val board = ExerciseRecordsCalc.compute(listOf(r(0.0, 12, "a"), r(20.0, 8, "a")))
        assertThat(RecordText.repsAtWeight(board, WeightUnit.KILOGRAMS)).containsExactly("20 kg x 8", "Bodyweight x 12").inOrder()
        assertThat(RecordText.lines(board, WeightUnit.KILOGRAMS).map { it.title }).contains("Most reps, no added weight")
    }

    // --- calculators --------------------------------------------------------------------------------------------------

    @Test
    fun `the calculator estimate uses the chosen formula and refuses unusable input`() {
        assertThat(TrainingCalculators.estimate(100.0, 5)).isWithin(0.001).of(116.667)
        OneRepMax.formula = OneRepMaxFormula.LOMBARDI
        assertThat(TrainingCalculators.estimate(100.0, 5)).isWithin(0.001).of(100 * Math.pow(5.0, 0.10))
        assertThat(TrainingCalculators.estimate(null, 5)).isNull()
        assertThat(TrainingCalculators.estimate(100.0, null)).isNull()
        assertThat(TrainingCalculators.estimate(0.0, 5)).isNull()
        assertThat(TrainingCalculators.estimate(100.0, 0)).isNull()
        assertThat(TrainingCalculators.estimate(100.0, TrainingCalculators.MAX_REPS + 1)).isNull()
        assertThat(TrainingCalculators.estimate(Double.NaN, 5)).isNull()
    }

    @Test
    fun `the calculator warns about high reps only`() {
        assertThat(TrainingCalculators.caution(5)).isNull()
        assertThat(TrainingCalculators.caution(10)).isNull()
        assertThat(TrainingCalculators.caution(11)).isNotNull()
        assertThat(TrainingCalculators.caution(null)).isNull()
    }

    @Test
    fun `the percentage table runs 50 to 100 percent`() {
        val rows = TrainingCalculators.percentTable(200.0)
        assertThat(rows.map { it.percent }).containsExactly(50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100).inOrder()
        assertThat(rows.first().weightKg).isEqualTo(100.0)
        assertThat(rows.last().weightKg).isEqualTo(200.0)
        assertThat(rows.first { it.percent == 85 }.weightKg).isWithin(1e-9).of(170.0)
        assertThat(TrainingCalculators.percentTable(null)).isEmpty()
        assertThat(TrainingCalculators.percentTable(0.0)).isEmpty()
        assertThat(TrainingCalculators.percentTable(-3.0)).isEmpty()
    }

    @Test
    fun `the plate loader reuses the plate maths in both units`() {
        val kgPlates = PlateDefaults.platesCsvKg(WeightUnit.KILOGRAMS).split(',').map { it.toDouble() }
        val load = TrainingCalculators.plates(100.0, 20.0, kgPlates)
        assertThat(load.perSide).containsExactly(25.0, 15.0).inOrder()
        assertThat(load.isExact).isTrue()
        assertThat(TrainingCalculators.platesText(load, WeightUnit.KILOGRAMS)).isEqualTo("1 x 25, 1 x 15")

        val lbBar = PlateDefaults.barKg(WeightUnit.POUNDS)
        val lbPlates = PlateDefaults.platesCsvKg(WeightUnit.POUNDS).split(',').map { it.toDouble() }
        val target = WeightUnit.POUNDS.toKg(225.0)
        val lb = TrainingCalculators.plates(target, lbBar, lbPlates)
        assertThat(TrainingCalculators.platesText(lb, WeightUnit.POUNDS)).isEqualTo("2 x 45")
        assertThat(TrainingCalculators.barText(lbBar, WeightUnit.POUNDS)).isEqualTo("Bar 45 lb")
    }

    @Test
    fun `a target at or under the bar loads nothing`() {
        val load = TrainingCalculators.plates(20.0, 20.0, listOf(20.0))
        assertThat(load.perSide).isEmpty()
        assertThat(TrainingCalculators.platesText(load, WeightUnit.KILOGRAMS)).isEqualTo("Bar only")
    }
}
