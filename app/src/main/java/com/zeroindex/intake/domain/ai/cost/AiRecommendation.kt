package com.zeroindex.intake.domain.ai.cost

import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.Suggestion
import com.zeroindex.intake.domain.ai.SuggestionBasis
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSuite

/** What the cost screen says about the model for a task. */
data class Recommendation(val modelId: String?, val text: String, val benchmarked: Boolean)

/**
 * A recommendation exists only where the benchmark produced one (the cheapest model that passed). Without
 * benchmark data the screen says so rather than naming a favourite: no vendor is preferred in code.
 */
object AiRecommendation {
    fun forTask(task: AiTask, suggestion: Suggestion): Recommendation {
        val pick = suggestion.suggested
        return when {
            suggestion.basis == SuggestionBasis.BENCHMARKED && pick != null ->
                Recommendation(pick.model.id, "Recommended by your benchmark: the cheapest model that passed", true)
            task in BenchmarkSuite.tasks -> Recommendation(null, "Run the benchmark to pick", false)
            else -> Recommendation(null, "Not in the benchmark yet, so the cheapest capable model is used", false)
        }
    }
}
