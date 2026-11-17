package com.zeroindex.intake.ui.screens.swap

import com.zeroindex.intake.data.ai.planb.SearchArea
import com.zeroindex.intake.data.nearby.NearbyRequest
import com.zeroindex.intake.data.nearby.NearbyRunResult
import com.zeroindex.intake.data.nearby.NearbySearchRunner
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.domain.nearby.NearbyPipeline
import com.zeroindex.intake.domain.nearby.NearbyPlace
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

    /** Restaurants the last run found no menu for, nearest first: what "Look up the rest with AI" works on. */
    private var missing: List<NearbyPlace> = emptyList()
    private var lastRequest: NearbyRequest? = null

    /** The cost already added to [NearbyState.costUsd], so a second run on the same list adds only what it spent. */
    private var costCounted = 0.0

    val running: Boolean get() = job?.isActive == true

    fun start(area: SearchArea.Coordinates) {
        if (running) return
        val now = read()
        missing = emptyList()
        costCounted = 0.0
        write { it.copy(pipeline = NearbyPipeline.reduce(PipelineState(), PipelineEvent.Started), error = null, offerAiFallback = false, foundAt = null, aiPending = 0) }
        val request = NearbyRequest(area, now.budget, mode, now.useAi && now.hasKey, planned())
        lastRequest = request
        launchRun { runner.run(request, onEvent = ::onEvent, onEntries = onEntries) }
    }

    /** "Look up the rest with AI": the next batched calls for the restaurants still without a menu. Only runs from a tap. */
    fun lookUpRest() {
        if (running || missing.isEmpty()) return
        val request = lastRequest ?: return
        if (!read().hasKey) return
        val pending = missing
        launchRun { runner.continueWithAi(pending, request.copy(useAi = true), onEvent = ::onEvent, onEntries = onEntries) }
    }

    private fun onEvent(e: PipelineEvent) = write { s -> s.copy(pipeline = NearbyPipeline.reduce(s.pipeline, e)) }

    private fun launchRun(block: suspend () -> NearbyRunResult) {
        job = scope.launch {
            try {
                missing = block().missing
            } catch (e: CancellationException) {
                // Stop was pressed: the list keeps what it has and says so.
                missing = emptyList()
                withContext(NonCancellable) { write { s -> s.copy(pipeline = NearbyPipeline.reduce(s.pipeline, PipelineEvent.Stopped)) } }
                throw e
            } finally {
                withContext(NonCancellable) {
                    write { s ->
                        val spent = s.pipeline.costUsd - costCounted
                        costCounted = s.pipeline.costUsd
                        s.copy(
                            foundAt = clock.nowMillis(),
                            offerAiFallback = s.pipeline.stage == SearchStage.FAILED && s.hasKey,
                            error = if (s.pipeline.stage == SearchStage.FAILED) s.pipeline.error else s.error,
                            resultCount = s.pipeline.picks,
                            costUsd = (s.costUsd ?: 0.0) + spent,
                            aiPending = if (s.pipeline.stage == SearchStage.DONE) missing.size else 0,
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
