package com.zeroindex.intake.ui.screens.swap

import com.zeroindex.intake.data.ai.planb.SearchArea
import com.zeroindex.intake.data.nearby.NearbyRequest
import com.zeroindex.intake.data.nearby.NearbySearchRunner
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.domain.nearby.NearbyPipeline
import com.zeroindex.intake.domain.nearby.PipelineEvent
import com.zeroindex.intake.domain.nearby.PipelineState
import com.zeroindex.intake.domain.nearby.SearchStage
import com.zeroindex.intake.domain.nearby.TravelBudget
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.swap.NearbyMode
import com.zeroindex.intake.domain.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the menu-by-menu nearby search for a screen's view model and keeps its [NearbyState] up to date. The screens
 * differ only in what they do with a restaurant's picks ([onEntries]) and in the ranking [mode].
 */
class NearbySearchHost(
    private val scope: CoroutineScope,
    private val runner: NearbySearchRunner,
    private val clock: Clock,
    private val mode: NearbyMode,
    private val planned: () -> Macros?,
    private val read: () -> NearbyState,
    private val write: ((NearbyState) -> NearbyState) -> Unit,
    private val onEntries: suspend (List<SwapEntry>) -> Unit,
    private val afterRun: () -> Unit,
) {
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    fun start(area: SearchArea.Coordinates) {
        if (running) return
        val now = read()
        write { it.copy(pipeline = NearbyPipeline.reduce(PipelineState(), PipelineEvent.Started), error = null, offerAiFallback = false, foundAt = null) }
        val request = NearbyRequest(area, now.budget, mode, now.useAi && now.hasKey, planned())
        job = scope.launch {
            try {
                runner.run(request, onEvent = { e -> write { s -> s.copy(pipeline = NearbyPipeline.reduce(s.pipeline, e)) } }, onEntries = onEntries)
            } catch (e: CancellationException) {
                // Stop was pressed: the list keeps what it has and says so.
                withContext(NonCancellable) { write { s -> s.copy(pipeline = NearbyPipeline.reduce(s.pipeline, PipelineEvent.Stopped)) } }
                throw e
            } finally {
                withContext(NonCancellable) {
                    write { s ->
                        s.copy(
                            foundAt = clock.nowMillis(),
                            offerAiFallback = s.pipeline.stage == SearchStage.FAILED && s.hasKey,
                            error = if (s.pipeline.stage == SearchStage.FAILED) s.pipeline.error else s.error,
                            resultCount = s.pipeline.picks,
                            costUsd = (s.costUsd ?: 0.0) + s.pipeline.costUsd,
                        )
                    }
                    afterRun()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
