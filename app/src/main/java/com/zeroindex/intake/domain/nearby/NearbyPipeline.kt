package com.zeroindex.intake.domain.nearby

/** Where a restaurant's menu came from, shown on its row. */
enum class MenuOrigin(val label: String) {
    SAVED("your saved menu"),
    CHAIN("the chain's own page"),
    WEBSITE("the restaurant's website"),
    AI_PAGE("found with AI"),
    AI_ESTIMATE("AI estimate"),
}

enum class RowPhase { WAITING, LOOKING, READING, DONE, NO_MENU }

/** One restaurant in the live list. */
data class RestaurantRow(
    val key: String,
    val name: String,
    val distanceM: Double,
    val phase: RowPhase = RowPhase.WAITING,
    /** Healthier picks shown from this restaurant. */
    val picks: Int = 0,
    val origin: MenuOrigin? = null,
    /** Why there is no menu, or what step it is on. */
    val note: String? = null,
) {
    val finished: Boolean get() = phase == RowPhase.DONE || phase == RowPhase.NO_MENU

    /** The words on the row. */
    val statusText: String
        get() = when (phase) {
            RowPhase.WAITING -> "waiting"
            RowPhase.LOOKING -> "looking for menu…"
            RowPhase.READING -> "reading menu…"
            RowPhase.DONE -> when (picks) {
                0 -> "menu read, no picks that fit"
                1 -> "1 healthier pick"
                else -> "$picks healthier picks"
            }
            RowPhase.NO_MENU -> "no menu found"
        }
}

enum class SearchStage { IDLE, LOCATING, FINDING, WORKING, DONE, STOPPED, FAILED }

data class PipelineState(
    val stage: SearchStage = SearchStage.IDLE,
    val rows: List<RestaurantRow> = emptyList(),
    val error: String? = null,
    /** AI lookups used in this search, and what the ones that report a cost spent. */
    val aiLookups: Int = 0,
    val costUsd: Double = 0.0,
) {
    val running: Boolean get() = stage == SearchStage.LOCATING || stage == SearchStage.FINDING || stage == SearchStage.WORKING
    val picks: Int get() = rows.sumOf { it.picks }
    val withMenu: Int get() = rows.count { it.phase == RowPhase.DONE }
    val withoutMenu: Int get() = rows.count { it.phase == RowPhase.NO_MENU }
}

sealed interface PipelineEvent {
    data object Started : PipelineEvent
    data object Located : PipelineEvent
    data class Failed(val message: String) : PipelineEvent
    data class Restaurants(val rows: List<RestaurantRow>) : PipelineEvent
    data class Phase(val key: String, val phase: RowPhase, val note: String? = null) : PipelineEvent
    data class Menu(val key: String, val origin: MenuOrigin, val picks: Int) : PipelineEvent
    data class NoMenu(val key: String, val note: String?) : PipelineEvent
    data class AiUsed(val costUsd: Double) : PipelineEvent
    data object Stopped : PipelineEvent
}

/**
 * The search as a state machine, pure so it can be tested. The data layer feeds it events from up to two restaurants at
 * a time; each event is applied on its own, so order between restaurants does not matter. A restaurant only moves
 * forward (waiting, looking, reading, then done or no menu), and nothing changes after the person stops the search or it
 * has failed, so a late result from a call that was already in flight cannot reopen a stopped list.
 */
object NearbyPipeline {

    /** At most this many restaurants are worked on at once. */
    const val CONCURRENCY = 2

    /** At most this many restaurants are looked up in one search, nearest first, so a big city does not run for an hour. */
    const val MAX_RESTAURANTS = 30

    /** At most this many AI menu lookups in one search, when the person turned them on. */
    const val MAX_AI_LOOKUPS = 6

    fun reduce(state: PipelineState, event: PipelineEvent): PipelineState {
        if (state.stage == SearchStage.STOPPED || state.stage == SearchStage.FAILED) return state
        return when (event) {
            PipelineEvent.Started -> PipelineState(stage = SearchStage.LOCATING)
            PipelineEvent.Located -> state.copy(stage = SearchStage.FINDING)
            is PipelineEvent.Failed -> state.copy(stage = SearchStage.FAILED, error = event.message)
            is PipelineEvent.Restaurants ->
                if (event.rows.isEmpty()) state.copy(stage = SearchStage.DONE, rows = emptyList())
                else state.copy(stage = SearchStage.WORKING, rows = event.rows)
            is PipelineEvent.Phase -> update(state, event.key) { r ->
                if (r.finished || event.phase.ordinal < r.phase.ordinal) r else r.copy(phase = event.phase, note = event.note ?: r.note)
            }
            is PipelineEvent.Menu -> update(state, event.key) { it.copy(phase = RowPhase.DONE, picks = event.picks, origin = event.origin, note = null) }
            is PipelineEvent.NoMenu -> update(state, event.key) { it.copy(phase = RowPhase.NO_MENU, note = event.note) }
            is PipelineEvent.AiUsed -> state.copy(aiLookups = state.aiLookups + 1, costUsd = state.costUsd + event.costUsd)
            PipelineEvent.Stopped -> state.copy(stage = SearchStage.STOPPED)
        }
    }

    private fun update(state: PipelineState, key: String, change: (RestaurantRow) -> RestaurantRow): PipelineState {
        val rows = state.rows.map { if (it.key == key) change(it) else it }
        val done = rows.isNotEmpty() && rows.all { it.finished }
        return state.copy(rows = rows, stage = if (done) SearchStage.DONE else state.stage)
    }

    fun initialRows(places: List<NearbyPlace>): List<RestaurantRow> =
        places.take(MAX_RESTAURANTS).map { RestaurantRow(it.osmId, it.displayName, it.distanceM) }

    /** One line for the top of the list. */
    fun summary(state: PipelineState): String = when (state.stage) {
        SearchStage.IDLE -> ""
        SearchStage.LOCATING -> "Finding where you are…"
        SearchStage.FINDING -> "Finding restaurants on the map…"
        SearchStage.WORKING -> "${state.rows.count { it.finished }} of ${state.rows.size} restaurants checked · ${state.picks} picks so far"
        SearchStage.DONE ->
            if (state.rows.isEmpty()) "No restaurants found in range."
            else "${state.rows.size} restaurants checked · ${state.withMenu} with a menu · ${state.picks} picks"
        SearchStage.STOPPED -> "Stopped. ${state.rows.count { it.finished }} of ${state.rows.size} restaurants were checked · ${state.picks} picks"
        SearchStage.FAILED -> state.error ?: "The search failed."
    }
}
