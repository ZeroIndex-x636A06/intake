package com.zeroindex.intake.ui.screens.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.remote.youtube.VideoError
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.video.VideoRepository
import com.zeroindex.intake.domain.settings.WeeklySets
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.training.VolumeStatus
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.videos.FilteredVideos
import com.zeroindex.intake.domain.videos.RecommendInput
import com.zeroindex.intake.domain.videos.RecommendedQueries
import com.zeroindex.intake.domain.videos.ScoredVideo
import com.zeroindex.intake.domain.videos.VideoBrowse
import com.zeroindex.intake.domain.videos.VideoLength
import com.zeroindex.intake.domain.videos.VideoMeta
import com.zeroindex.intake.domain.videos.VideoStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of the Recommended page: the plain reason, and the videos that came back for it. */
data class RecommendedRowUi(val reason: String, val videos: List<ScoredVideo>)

sealed interface RecommendedUi {
    data object Idle : RecommendedUi
    data object Loading : RecommendedUi
    data class Ready(val rows: List<RecommendedRowUi>, val personalised: Boolean) : RecommendedUi
    data class Failed(val message: String) : RecommendedUi
}

data class VideosUiState(
    val hasKey: Boolean = false,
    val query: String = "",
    val style: VideoStyle? = null,
    val length: VideoLength? = null,
    val onlyWorkouts: Boolean = true,
    val hideShorts: Boolean = true,
    val searching: Boolean = false,
    /** The words of the search whose results are shown, or null when no search was run. */
    val searchedFor: String? = null,
    val results: FilteredVideos? = null,
    val resultsFromCache: Boolean = false,
    val searchError: String? = null,
    val recommended: RecommendedUi = RecommendedUi.Idle,
    val saved: List<VideoMeta> = emptyList(),
    val quotaHint: String = "",
    val link: String = "",
    val linkBusy: Boolean = false,
    val linkError: String? = null,
)

/**
 * The Videos home (IA v5 7a): search with the person's own YouTube key, the workout filter, Recommended built from local
 * data, saved videos, and the no-key way in (a pasted link). Nothing here runs by itself except a cache-first Recommended
 * load when the page is opened with a key; every search the person types is one explicit submit.
 */
@HiltViewModel
class VideosViewModel @Inject constructor(
    private val videos: VideoRepository,
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private data class Raw(
        val query: String = "",
        val style: VideoStyle? = null,
        val length: VideoLength? = null,
        val onlyOverride: Boolean? = null,
        val shortsOverride: Boolean? = null,
        val searching: Boolean = false,
        val searchedFor: String? = null,
        val results: List<VideoMeta>? = null,
        val fromCache: Boolean = false,
        val showEverything: Boolean = false,
        val error: String? = null,
        val link: String = "",
        val linkBusy: Boolean = false,
        val linkError: String? = null,
    )

    private val raw = MutableStateFlow(Raw())
    private val recommended = MutableStateFlow<RecommendedUi>(RecommendedUi.Idle)
    private val catalog = MutableStateFlow<List<String>>(emptyList())
    /** Bumped when the page comes back to the front, so a key added in Settings is noticed. */
    private val keyTick = MutableStateFlow(0)
    private var recommendedFor: Boolean? = null

    val state: StateFlow<VideosUiState> = combine(
        raw,
        recommended,
        settings.settings,
        videos.observeSavedMeta(),
        combine(catalog, keyTick) { names, _ -> names },
    ) { r, rec, app, saved, names ->
        val only = r.onlyOverride ?: app.onlyWorkouts
        val noShorts = r.shortsOverride ?: app.hideShorts
        val filtered = r.results?.let { list ->
            VideoBrowse.apply(VideoBrowse.score(list, names), only, r.showEverything, r.length, hideShorts = noShorts)
        }
        VideosUiState(
            hasKey = videos.hasKey(),
            query = r.query,
            style = r.style,
            length = r.length,
            onlyWorkouts = only,
            hideShorts = noShorts,
            searching = r.searching,
            searchedFor = r.searchedFor,
            results = filtered,
            resultsFromCache = r.fromCache,
            searchError = r.error,
            recommended = scoreRecommended(rec, names, only, noShorts),
            saved = saved,
            quotaHint = videos.quotaHint(),
            link = r.link,
            linkBusy = r.linkBusy,
            linkError = r.linkError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideosUiState(hasKey = videos.hasKey()))

    init {
        viewModelScope.launch {
            // The 30-day rule: cached API data older than that is removed whenever this page is opened.
            runCatching { videos.purgeExpired() }
            runCatching { catalog.value = training.exercises().map { it.name } }
        }
    }

    /** The recommended rows are stored raw and scored when drawn, so "Workouts only" can change without a new call. */
    private var rawRows: List<Pair<String, List<VideoMeta>>> = emptyList()

    private fun scoreRecommended(rec: RecommendedUi, names: List<String>, onlyWorkouts: Boolean, hideShorts: Boolean): RecommendedUi {
        if (rec !is RecommendedUi.Ready) return rec
        val seen = mutableSetOf<String>()
        val rows = rawRows.map { (reason, list) ->
            val scored = VideoBrowse.score(list.filter { seen.add(it.id) }, names)
            RecommendedRowUi(reason, VideoBrowse.apply(scored, onlyWorkouts, showEverything = false, length = null, hideShorts = hideShorts).shown.take(8))
        }.filter { it.videos.isNotEmpty() }
        return rec.copy(rows = rows)
    }

    /** Call when the Videos page is shown or comes back to the front. */
    fun onShown() {
        keyTick.update { it + 1 }
        if (videos.hasKey()) loadRecommended(force = false)
    }

    fun setQuery(text: String) = raw.update { it.copy(query = text) }

    fun setLink(text: String) = raw.update { it.copy(link = text, linkError = null) }

    /** The "Workouts only" chip: for this visit only. The default stays in Settings > Videos. */
    fun toggleOnlyWorkouts() {
        val now = state.value.onlyWorkouts
        raw.update { it.copy(onlyOverride = !now) }
    }

    /** The "Hide Shorts" chip: for this visit only. The default stays in Settings > Videos. */
    fun toggleHideShorts() {
        val now = state.value.hideShorts
        raw.update { it.copy(shortsOverride = !now) }
    }

    fun setLength(length: VideoLength?) = raw.update { it.copy(length = if (it.length == length) null else length) }

    /** A style chip adds its word to the search and runs it: one tap, one search. */
    fun toggleStyle(style: VideoStyle) {
        val next = if (raw.value.style == style) null else style
        raw.update { it.copy(style = next) }
        if (next != null || raw.value.query.isNotBlank()) search()
    }

    /** "Show everything": lifts the workout filter for the search on screen only. */
    fun showEverything() = raw.update { it.copy(showEverything = true) }

    /** Runs a search with the typed words and the style chip. One explicit submit costs about 100 quota units, less with a recent cache. */
    fun search() {
        val r = raw.value
        val words = VideoBrowse.searchQuery(r.query, r.style)
        if (words.isBlank() || r.searching) return
        raw.update { it.copy(searching = true, error = null, showEverything = false) }
        viewModelScope.launch {
            try {
                val result = videos.search(words, VideoBrowse.cacheFilters(r.style))
                raw.update { it.copy(searching = false, searchedFor = words, results = result.videos, fromCache = result.fromCache) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                raw.update { it.copy(searching = false, error = messageOf(e)) }
            } finally {
                keyTick.update { it + 1 }
            }
        }
    }

    /** Back to the Recommended page. */
    fun clearSearch() = raw.update { it.copy(query = "", style = null, results = null, searchedFor = null, error = null, showEverything = false) }

    fun loadRecommended(force: Boolean) {
        if (recommended.value is RecommendedUi.Loading) return
        viewModelScope.launch {
            val app = settings.settings.first()
            if (!force && recommended.value is RecommendedUi.Ready && recommendedFor == app.personaliseVideos) return@launch
            recommended.value = RecommendedUi.Loading
            try {
                val week = WeeklyVolume.weekRange(clock.nowMillis(), clock.zone())
                val targets = WeeklySets.parse(app.weeklySetsCsv).mapKeys { (k, _) -> BodyRegion.valueOf(k.name) }
                val volumes = WeeklyVolume.compute(training.observeVolumeSets(week).first(), targets, week)
                val under = volumes.filter { it.status == VolumeStatus.UNDER }.sortedBy { it.sets / it.target }.map { it.region }
                val savedTitles = videos.observeSavedMeta().first().map { it.title }
                val input = RecommendInput(
                    underTarget = under,
                    equipment = app.profile.equipment,
                    programFocus = training.activeProgram()?.name,
                    savedTitles = savedTitles,
                    sessionMinutes = app.profile.sessionMinutes,
                )
                val queries = RecommendedQueries.build(app.personaliseVideos, input)
                val rows = queries.map { q -> q.reason to videos.search(q.query).videos }
                rawRows = rows
                recommendedFor = app.personaliseVideos
                recommended.value = RecommendedUi.Ready(emptyList(), app.personaliseVideos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recommended.value = RecommendedUi.Failed(messageOf(e))
            } finally {
                keyTick.update { it + 1 }
            }
        }
    }

    /** "Paste a YouTube link": reads the id, fetches the title (oEmbed when there is no key) and reports it. */
    fun openLink(onFound: (String) -> Unit) {
        val text = raw.value.link
        if (text.isBlank() || raw.value.linkBusy) return
        raw.update { it.copy(linkBusy = true, linkError = null) }
        viewModelScope.launch {
            try {
                val meta = videos.fromLink(text)
                if (meta == null) {
                    raw.update { it.copy(linkBusy = false, linkError = "That does not look like a YouTube video link.") }
                } else {
                    raw.update { it.copy(linkBusy = false, link = "") }
                    onFound(meta.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                raw.update { it.copy(linkBusy = false, linkError = messageOf(e)) }
            }
        }
    }

    fun unsave(videoId: String) = viewModelScope.launch { videos.unsave(videoId) }

    /** "Clear video cache": removes everything fetched from YouTube (the saved videos stay). */
    fun clearCache(onDone: () -> Unit) = viewModelScope.launch {
        videos.clearCache()
        rawRows = emptyList()
        recommended.value = RecommendedUi.Idle
        raw.update { it.copy(results = null, searchedFor = null) }
        onDone()
    }

    private fun messageOf(e: Exception): String = when (e) {
        is VideoError -> e.message.orEmpty()
        else -> "Something went wrong reading YouTube's answer. Try again in a moment."
    }
}
