package com.zeroindex.intake.data.nearby

import com.zeroindex.intake.data.ai.planb.PlanBFinder
import com.zeroindex.intake.data.ai.planb.SearchArea
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.data.swap.SwapRepository
import com.zeroindex.intake.domain.nearby.NearbyAiBatch
import com.zeroindex.intake.domain.nearby.NearbyPipeline
import com.zeroindex.intake.domain.nearby.NearbyPlace
import com.zeroindex.intake.domain.nearby.OverpassParser
import com.zeroindex.intake.domain.nearby.PipelineEvent
import com.zeroindex.intake.domain.nearby.RowPhase
import com.zeroindex.intake.domain.nearby.TravelBudget
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.swap.NearbyMode
import com.zeroindex.intake.domain.swap.NearbyRanking
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** What the person asked for with one tap on Search. */
data class NearbyRequest(
    val area: SearchArea.Coordinates,
    val budget: TravelBudget,
    val mode: NearbyMode,
    /** The "Use AI to find menus I can't" switch on the card. Off unless the person turned it on for this search. */
    val useAi: Boolean,
    /** The planned meal for [NearbyMode.SWAP_VS_PLANNED]. */
    val planned: Macros?,
)

/** What a run left: the restaurants no menu was found for, nearest first, so the person can ask AI about them next. */
data class NearbyRunResult(val missing: List<NearbyPlace>)

/**
 * One nearby search, end to end: the map request, then each restaurant's menu with no AI, two at a time, nearest first.
 * Results are handed over as soon as a restaurant's menu is in, so the list grows while the rest are still being looked
 * up. When the person turned AI on, the restaurants nothing else could find go to AI afterwards, nearest first, up to 8
 * to a call and at most [NearbyPipeline.MAX_AI_LOOKUPS] calls; [continueWithAi] does the next ones when asked. The same
 * runner serves Eating out (healthier ranking) and Swap (closeness to the planned meal).
 */
class NearbySearchRunner @Inject constructor(
    private val overpass: OverpassClient,
    private val menus: NearbyMenuSearch,
    private val finder: PlanBFinder,
    private val repo: SwapRepository,
) {
    /** Runs until done or cancelled. Every outcome is an event; a failure is an event too, never an exception. */
    suspend fun run(
        request: NearbyRequest,
        onEvent: (PipelineEvent) -> Unit,
        onEntries: suspend (List<SwapEntry>) -> Unit,
    ): NearbyRunResult {
        val places: List<NearbyPlace> = try {
            onEvent(PipelineEvent.Located)
            OverpassParser.dedupeByBrand(overpass.restaurantsAround(request.area.lat, request.area.lng, request.budget.meters))
        } catch (e: NearbyMapException) {
            onEvent(PipelineEvent.Failed(e.message ?: "The map search failed."))
            return NearbyRunResult(emptyList())
        }
        val rows = NearbyPipeline.initialRows(places)
        onEvent(PipelineEvent.Restaurants(rows))
        val work = places.take(NearbyPipeline.MAX_RESTAURANTS)
        val gate = Semaphore(NearbyPipeline.CONCURRENCY)
        val missing = java.util.Collections.synchronizedList(ArrayList<NearbyPlace>())
        coroutineScope {
            for (place in work) {
                launch {
                    gate.withPermit {
                        if (!one(place, request, onEvent, onEntries)) missing += place
                    }
                }
            }
        }
        val pending = missing.sortedBy { it.distanceM }
        if (!request.useAi || pending.isEmpty()) {
            pending.forEach { onEvent(PipelineEvent.NoMenu(it.osmId, null)) }
            return NearbyRunResult(pending)
        }
        return NearbyRunResult(aiPhase(pending, request, onEvent, onEntries))
    }

    /** "Look up the rest with AI": the next batches for [missing], on the person's tap. Returns what is still missing. */
    suspend fun continueWithAi(
        missing: List<NearbyPlace>,
        request: NearbyRequest,
        onEvent: (PipelineEvent) -> Unit,
        onEntries: suspend (List<SwapEntry>) -> Unit,
    ): NearbyRunResult {
        val pending = missing.sortedBy { it.distanceM }
        onEvent(PipelineEvent.Resume(pending.take(NearbyAiBatch.coveredBy(pending.size)).map { it.osmId }.toSet(), AI_WAITING))
        return NearbyRunResult(aiPhase(pending, request, onEvent, onEntries))
    }

    /**
     * Batches [pending] (nearest first) into at most [NearbyPipeline.MAX_AI_LOOKUPS] AI calls. Places a call found a menu
     * for show their picks; the rest are "no menu found", and places beyond the cap say they were not looked up. The
     * result is every place still without a menu, nearest first.
     */
    private suspend fun aiPhase(
        pending: List<NearbyPlace>,
        request: NearbyRequest,
        onEvent: (PipelineEvent) -> Unit,
        onEntries: suspend (List<SwapEntry>) -> Unit,
    ): List<NearbyPlace> {
        val still = ArrayList<NearbyPlace>()
        val batches = NearbyAiBatch.plan(pending)
        val covered = batches.flatten().map { it.osmId }.toSet()
        for (batch in batches) {
            coroutineContext.ensureActive()
            batch.forEach { onEvent(PipelineEvent.Phase(it.osmId, RowPhase.LOOKING, AI_ASKING)) }
            val outcome = menus.lookupBatch(
                batch,
                onPhase = { place, phase -> onEvent(PipelineEvent.Phase(place.osmId, phase)) },
                onAiCost = { onEvent(PipelineEvent.AiUsed(it)) },
            )
            for (place in batch) {
                val menu = outcome.found[place.osmId]
                if (menu == null) {
                    still += place
                    onEvent(PipelineEvent.NoMenu(place.osmId, outcome.error))
                } else if (!showMenu(place, menu, request, onEvent, onEntries)) {
                    still += place
                }
            }
        }
        for (place in pending) {
            if (place.osmId !in covered) {
                still += place
                onEvent(PipelineEvent.NoMenu(place.osmId, "not looked up yet"))
            }
        }
        return still.sortedBy { it.distanceM }
    }

    /** True when a menu was found and shown (even with no picks that fit); false when none was found. */
    private suspend fun one(
        place: NearbyPlace,
        request: NearbyRequest,
        onEvent: (PipelineEvent) -> Unit,
        onEntries: suspend (List<SwapEntry>) -> Unit,
    ): Boolean {
        val key = place.osmId
        try {
            val menu = menus.discover(place, onPhase = { onEvent(PipelineEvent.Phase(key, it)) })
            if (menu == null) {
                // With AI on, the row waits for the batched lookup instead of finishing as "no menu found".
                if (request.useAi) onEvent(PipelineEvent.Phase(key, RowPhase.LOOKING, AI_WAITING))
                return false
            }
            return showMenu(place, menu, request, onEvent, onEntries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onEvent(PipelineEvent.NoMenu(key, e.message))
            return true
        }
    }

    /** True when the menu was read and shown (even with no picks that fit); false when it could not be read. */
    private suspend fun showMenu(
        place: NearbyPlace,
        menu: DiscoveredMenu,
        request: NearbyRequest,
        onEvent: (PipelineEvent) -> Unit,
        onEntries: suspend (List<SwapEntry>) -> Unit,
    ): Boolean {
        val key = place.osmId
        try {
            onEvent(PipelineEvent.Phase(key, RowPhase.READING))
            val options = finder.menuOptionsOfSource(menu.sourceId, place.distanceMiles)
            val entries = options.map(repo::foundEntry)
            val shown = pick(entries, request)
            if (shown.isNotEmpty()) onEntries(shown)
            onEvent(PipelineEvent.Menu(key, menu.origin, shown.count { !it.option.blocked }))
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onEvent(PipelineEvent.NoMenu(key, e.message))
            return false
        }
    }

    /** The best few of one restaurant's items, by the mode's own ranking, choosable ones first. */
    private suspend fun pick(entries: List<SwapEntry>, request: NearbyRequest): List<SwapEntry> {
        if (entries.isEmpty()) return emptyList()
        return when (request.mode) {
            NearbyMode.HEALTHIER -> repo.healthierRank(entries).take(PER_RESTAURANT)
            NearbyMode.SWAP_VS_PLANNED -> {
                val planned = request.planned ?: Macros(kcal = 600.0)
                entries.sortedWith(
                    compareBy<SwapEntry> { it.option.blocked }
                        .thenByDescending { NearbyRanking.closeness(it.option.macros, planned) },
                ).take(PER_RESTAURANT)
            }
        }
    }

    companion object {
        const val PER_RESTAURANT = 5
        const val AI_WAITING = "waiting for the AI lookup"
        const val AI_ASKING = "asking AI…"
    }
}
