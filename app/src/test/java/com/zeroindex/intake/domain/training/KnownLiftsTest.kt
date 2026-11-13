package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import org.junit.Test

class KnownLiftsTest {

    @Test fun everyKeyLiftIsInTheCatalog() {
        KnownLifts.KEY_LIFTS.forEach { assertThat(ExerciseCatalog.byName(it)).isNotNull() }
    }

    @Test fun anAnswerNeedsASensibleWeightAndReps() {
        assertThat(KnownLifts.entry(100.0, 5)).isEqualTo(PastSet(100.0, 5))
        assertThat(KnownLifts.entry(100.0, null)).isEqualTo(PastSet(100.0, 5))
        assertThat(KnownLifts.entry(null, 5)).isNull()
        assertThat(KnownLifts.entry(0.0, 5)).isNull()
        assertThat(KnownLifts.entry(100.0, 0)).isNull()
        assertThat(KnownLifts.entry(100.0, 99)).isNull()
    }

    @Test fun answersBecomeHistoryByExerciseId() {
        val history = KnownLifts.asHistory(mapOf("Bench press" to PastSet(100.0, 5), "Back squat" to null, "Not a lift" to PastSet(50.0, 5)))
        assertThat(history.keys).containsExactly(ExerciseCatalog.requireByName("Bench press").id)
    }

    @Test fun aKnownBenchSetsTheStartingWeightInsteadOfABodyWeightGuess() {
        val bench = ExerciseCatalog.requireByName("Bench press")
        val context = StartingWeightContext(experience = Experience.SOME, bodyweightKg = 80.0, sex = null)
        val known = KnownLifts.asHistory(mapOf("Bench press" to PastSet(100.0, 5)))
        val withAnswer = StartingWeightEstimator.estimate(bench, 6, 10, context) { id -> known[id].orEmpty() }
        val guess = StartingWeightEstimator.estimate(bench, 6, 10, context) { emptyList() }
        assertThat(withAnswer?.weightKg).isNotNull()
        assertThat(withAnswer!!.weightKg).isGreaterThan(guess!!.weightKg)
        assertThat(withAnswer.weightKg).isLessThan(100.0)
    }
}
