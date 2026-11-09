package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.cost.AiBudget
import com.zeroindex.intake.domain.ai.cost.AiRecommendation
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSuite
import org.junit.Test

class AiGuardTest {

    private val ok = AiBudget.status(2.0, 0.0)

    private fun check(
        feature: AiFeature = AiFeature.PLAN_B,
        trigger: AiTrigger? = AiTrigger.USER_ACTION,
        enabled: Boolean = true,
        hasKey: Boolean = true,
        optIns: Set<AiFeature> = emptySet(),
        budget: com.zeroindex.intake.domain.ai.cost.BudgetStatus = ok,
    ) = AiGuard.check(feature, trigger, enabled, hasKey, optIns, budget)

    @Test
    fun `a user action with the switch on, a key and budget left is allowed`() {
        assertThat(check()).isNull()
    }

    @Test
    fun `no trigger is refused first`() {
        assertThat(check(trigger = null, enabled = false)).isEqualTo(GuardRefusal.NO_TRIGGER)
    }

    @Test
    fun `the master switch off refuses everything`() {
        AiFeature.entries.forEach { f ->
            assertThat(check(feature = f, enabled = false)).isEqualTo(GuardRefusal.SWITCH_OFF)
        }
    }

    @Test
    fun `an exhausted budget refuses everything`() {
        AiFeature.entries.forEach { f ->
            assertThat(check(feature = f, budget = AiBudget.status(1.0, 1.0))).isEqualTo(GuardRefusal.BUDGET)
        }
    }

    @Test
    fun `no key is refused`() {
        assertThat(check(hasKey = false)).isEqualTo(GuardRefusal.NO_KEY)
    }

    @Test
    fun `only the schedulable features may use a schedule, and only when opted in`() {
        val schedulable = AiFeature.entries.filter { it.schedulable }
        assertThat(schedulable).containsExactly(AiFeature.MENU_IMPORT, AiFeature.DAILY_INSIGHT, AiFeature.COACH_REWORD)
        AiFeature.entries.forEach { f ->
            val allowed = check(feature = f, trigger = AiTrigger.OPT_IN_SCHEDULE, optIns = AiFeature.entries.toSet())
            if (f.schedulable) assertThat(allowed).isNull() else assertThat(allowed).isEqualTo(GuardRefusal.TRIGGER_NOT_ALLOWED)
        }
        schedulable.forEach { f ->
            assertThat(check(feature = f, trigger = AiTrigger.OPT_IN_SCHEDULE)).isEqualTo(GuardRefusal.NOT_OPTED_IN)
        }
    }

    @Test
    fun `features that are never automatic have only the user action trigger`() {
        listOf(AiFeature.PHOTO_ESTIMATE, AiFeature.RECIPE_HUNTER, AiFeature.PLAN_B, AiFeature.RECIPE_TRY_AI, AiFeature.BENCHMARK)
            .forEach { assertThat(it.allowedTriggers).containsExactly(AiTrigger.USER_ACTION) }
    }

    @Test
    fun `the opt-in features are the ones that count as zero while switched off`() {
        assertThat(AiFeature.entries.filter { it.optInSwitch })
            .containsExactly(AiFeature.DAILY_INSIGHT, AiFeature.COACH_REWORD)
    }

    // --- recommendation -----------------------------------------------------------------------------------

    private fun suggestion(basis: SuggestionBasis?, id: String?): Suggestion {
        val model = id?.let {
            ModelInfo(it, it, "", 1_000_000, 1.0, 2.0, 0.0, 0.0, true, false, true, true, true)
        }
        return Suggestion(model?.let { RankedModel(it, 0.001, null) }, basis, emptyList())
    }

    @Test
    fun `a benchmarked pick is recommended by name`() {
        val r = AiRecommendation.forTask(AiTask.PLAN_B, suggestion(SuggestionBasis.BENCHMARKED, "a/b"))
        assertThat(r.modelId).isEqualTo("a/b")
        assertThat(r.benchmarked).isTrue()
    }

    @Test
    fun `without benchmark data it says to run the benchmark and names no model`() {
        val shortlist = AiRecommendation.forTask(AiTask.PLAN_B, suggestion(SuggestionBasis.SHORTLIST, "a/b"))
        assertThat(shortlist.modelId).isNull()
        assertThat(shortlist.text).isEqualTo("Run the benchmark to pick")
        val none = AiRecommendation.forTask(AiTask.MENU_INGESTION, suggestion(null, null))
        assertThat(none.text).isEqualTo("Run the benchmark to pick")
    }

    @Test
    fun `a task the benchmark cannot cover is not told to run it`() {
        assertThat(AiTask.VISION_TRACKER in BenchmarkSuite.tasks).isFalse()
        val r = AiRecommendation.forTask(AiTask.VISION_TRACKER, suggestion(SuggestionBasis.SHORTLIST, "a/b"))
        assertThat(r.modelId).isNull()
        assertThat(r.text).doesNotContain("Run the benchmark")
    }
}
