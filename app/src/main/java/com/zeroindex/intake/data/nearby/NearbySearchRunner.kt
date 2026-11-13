package com.zeroindex.intake.data.nearby

import com.zeroindex.intake.data.ai.planb.PlanBFinder
import com.zeroindex.intake.data.ai.planb.SearchArea
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.data.swap.SwapRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
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

/**
 * One nearby search, end to end: the map request, then each restaurant's menu, two at a time, nearest first. Results are
 * handed over as soon as a restaurant's menu is in, so the list grows while the rest are still being looked up. The same
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
    ) {
        val places: List<NearbyPlace> = try {
            onEvent(PipelineEvent.Located)
            OverpassParser.dedupeByBrand(overpass.restaurantsAround(request.area.lat, request.area.lng, request.budget.meters))
        } catch (e: NearbyMapException) {
            onEvent(PipelineEvent.Failed(e.message ?: "The map search failed."))
            return
        }
        val rows = NearbyPipeline.initialRows(places)
        onEvent(PipelineEvent.Restaurants(rows))
        val work = places.take(NearbyPipeline.MAX_RESTAURANTS)
        val allowance = if (request.useAi) AiAllowance(NearbyPipeline.MAX_AI_LOOKUPS) else null
        val gate = Semaphore(NearbyPipeline.CONCURRENCY)
        coroutineScope {
            for (place in work) {
                launch {
                    gate.withPermit { one(place, request, allowance, onEvent, onEntries) }
                }
            }
        }
    }

    private suspend fun one(
        place: NearbyPlace,
        request: NearbyRequest,
        allowance: AiAllowance?,
        onEvent: (PipelineEvent) -> Unit,
        onEntries: suspend (List<SwapEntry>) -> Unit,
    ) {
        val key = place.osmId
        try {
            val menu = menus.discover(
                place, allowance,
                onPhase = { onEvent(PipelineEvent.Phase(key, it)) },
                onAiCost = { onEvent(PipelineEvent.AiUsed(it)) },
            )
            if (menu == null) {
                onEvent(PipelineEvent.NoMenu(key, null))
                return
            }
            onEvent(PipelineEvent.Phase(key, RowPhase.READING))
            val options = finder.menuOptionsOfSource(menu.sourceId, place.distanceMiles)
            val entries = options.map(repo::foundEntry)
            val shown = pick(entries, request)
            if (shown.isNotEmpty()) onEntries(shown)
            onEvent(PipelineEvent.Menu(key, menu.origin, shown.count { !it.option.blocked }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onEvent(PipelineEvent.NoMenu(key, e.message))
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
    }
}
