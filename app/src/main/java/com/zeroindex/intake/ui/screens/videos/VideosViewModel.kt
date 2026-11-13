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
import com.zeroindex.intake.domain.videos.VideoPaging
import com.zeroindex.intake.domain.videos.VideoQuota
import com.zeroindex.intake.domain.videos.SavedSimilarity
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
data class RecommendedRowUi(
    val reason: String,
    val videos: List<ScoredVideo>,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

/** One Recommended row as fetched: the words, the videos so far and where paging is. */
private data class RawRow(
    val reason: String,
    val query: String,
    val videos: List<VideoMeta>,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
)

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
    val muscle: BodyRegion? = null,
    val filterCount: Int = 0,
    val onlyWorkouts: Boolean = true,
    val hideShorts: Boolean = true,
    val searching: Boolean = false,
    /** The words of the search whose results are shown, or null when no search was run. */
    val searchedFor: String? = null,
    val results: FilteredVideos? = null,
    val resultsFromCache: Boolean = false,
    val loadingMore: Boolean = false,
    /** False once YouTube has no further page, or paging stopped (page cap, low quota). */
    val canLoadMore: Boolean = false,
    /** True when paging stopped because the day's quota estimate is low. */
    val pagingStoppedForQuota: Boolean = false,
    val moreError: String? = null,
    val searchError: String? = null,
    val recommended: RecommendedUi = RecommendedUi.Idle,
    val saved: List<VideoMeta> = emptyList(),
    val quotaHint: String = "",
    val searchesLeft: Int = 0,
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
        val muscle: BodyRegion? = null,
        val page: Int = 1,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val moreError: String? = null,
        val searchFilters: String = "",
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
    private val rows = MutableStateFlow<List<RawRow>>(emptyList())
    private val catalog = MutableStateFlow<List<String>>(emptyList())
    /** Bumped when the page comes back to the front, so a key added in Settings is noticed. */
    private val keyTick = MutableStateFlow(0)
    private var recommendedFor: Boolean? = null

    val state: StateFlow<VideosUiState> = combine(
        raw,
        recommended,
        settings.settings,
        videos.observeSavedMeta(),
        combine(catalog, keyTick, rows) { names, _, rawRows -> names to rawRows },
    ) { r, rec, app, saved, namesAndRows ->
        val (names, rawRows) = namesAndRows
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
            muscle = r.muscle,
            filterCount = VideoBrowse.activeFilterCount(r.style, r.muscle, r.length),
            onlyWorkouts = only,
            hideShorts = noShorts,
            searching = r.searching,
            searchedFor = r.searchedFor,
            results = filtered,
            resultsFromCache = r.fromCache,
            loadingMore = r.loadingMore,
            canLoadMore = r.results != null && r.hasMore && r.page < VideoPaging.MAX_SEARCH_PAGES,
            pagingStoppedForQuota = r.results != null && r.hasMore && r.page < VideoPaging.MAX_SEARCH_PAGES &&
                VideoQuota.searchesLeft(videos.quotaUsedUnits()) <= VideoPaging.RESERVED_SEARCHES,
            moreError = r.moreError,
            searchError = r.error,
            recommended = scoreRecommended(rec, rawRows, names, only, noShorts),
            saved = saved,
            quotaHint = videos.quotaHint(),
            searchesLeft = videos.searchesLeft(),
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
    private fun scoreRecommended(rec: RecommendedUi, rawRows: List<RawRow>, names: List<String>, onlyWorkouts: Boolean, hideShorts: Boolean): RecommendedUi {
        if (rec !is RecommendedUi.Ready) return rec
        val seen = mutableSetOf<String>()
        val out = rawRows.map { row ->
            val scored = VideoBrowse.score(row.videos.filter { seen.add(it.id) }, names)
            RecommendedRowUi(
                row.reason,
                VideoBrowse.apply(scored, onlyWorkouts, showEverything = false, length = null, hideShorts = hideShorts).shown,
                hasMore = row.hasMore && row.page < VideoPaging.MAX_ROW_PAGES,
                loadingMore = row.loading,
            )
        }.filter { it.videos.isNotEmpty() || it.hasMore }
        return rec.copy(rows = out)
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

    /**
     * The Filters sheet's Apply: the style and the muscle group change what is asked of YouTube, so one search runs if either
     * changed (and there are words to send); the length only filters on the phone.
     */
    fun applyFilters(style: VideoStyle?, muscle: BodyRegion?, length: VideoLength?) {
        val r = raw.value
        val changed = r.style != style || r.muscle != muscle
        raw.update { it.copy(style = style, muscle = muscle, length = length) }
        if (changed && (style != null || muscle != null || r.query.isNotBlank())) search()
    }

    /** "Show everything": lifts the workout filter for the search on screen only. */
    fun showEverything() = raw.update { it.copy(showEverything = true) }

    /** Runs a search with the typed words and the style chip. One explicit submit costs about 100 quota units, less with a recent cache. */
    fun search() {
        val r = raw.value
        val words = VideoBrowse.searchQuery(r.query, r.style, r.muscle)
        if (words.isBlank() || r.searching) return
        val filters = VideoBrowse.cacheFilters(r.style, r.muscle)
        raw.update { it.copy(searching = true, error = null, moreError = null, showEverything = false) }
        viewModelScope.launch {
            try {
                val result = videos.search(words, filters)
                raw.update {
                    it.copy(
                        searching = false, searchedFor = words, searchFilters = filters, results = result.videos.distinctBy { v -> v.id },
                        fromCache = result.fromCache, page = 1, hasMore = result.hasMore, loadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                raw.update { it.copy(searching = false, error = messageOf(e)) }
            } finally {
                keyTick.update { it + 1 }
            }
        }
    }

    /** The next page of the search on screen, when the person scrolls near its end. One page is about 101 quota units, or none from the cache. */
    fun loadMore() {
        val r = raw.value
        val words = r.searchedFor ?: return
        val shown = r.results ?: return
        if (r.loadingMore || r.searching) return
        if (!VideoPaging.canLoadMore(r.page, VideoPaging.MAX_SEARCH_PAGES, r.hasMore, videos.quotaUsedUnits())) return
        raw.update { it.copy(loadingMore = true, moreError = null) }
        viewModelScope.launch {
            try {
                val next = videos.searchPage(words, r.searchFilters, r.page + 1)
                raw.update {
                    // The person may have started another search meanwhile: only add to the one these words belong to.
                    if (it.searchedFor != words || it.searchFilters != r.searchFilters) it.copy(loadingMore = false)
                    else it.copy(
                        loadingMore = false, page = r.page + 1, hasMore = next.hasMore,
                        results = (shown + next.videos).distinctBy { v -> v.id },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                raw.update { it.copy(loadingMore = false, moreError = messageOf(e)) }
            } finally {
                keyTick.update { it + 1 }
            }
        }
    }

    /** "More like this" on a saved video: a search from its channel and title words. */
    fun moreLikeThis(video: VideoMeta) {
        val q = SavedSimilarity.query(listOf(video)) ?: return
        raw.update { it.copy(query = q, style = null, muscle = null, length = null) }
        search()
    }

    /** Back to the Recommended page. */
    fun clearSearch() = raw.update {
        it.copy(query = "", style = null, muscle = null, results = null, searchedFor = null, error = null, moreError = null, showEverything = false, page = 1, hasMore = false)
    }

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
                val savedVideos = videos.observeSavedMeta().first()
                val input = RecommendInput(
                    underTarget = under,
                    equipment = app.profile.equipment,
                    programFocus = training.activeProgram()?.name,
                    savedTitles = savedVideos.map { it.title },
                    savedChannels = savedVideos.map { it.channelTitle },
                    sessionMinutes = app.profile.sessionMinutes,
                )
                val queries = RecommendedQueries.build(app.personaliseVideos, input)
                val fetched = queries.map { q ->
                    val first = videos.search(q.query)
                    RawRow(q.reason, q.query, first.videos, page = 1, hasMore = first.hasMore)
                }
                rows.value = fetched
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

    /** The next page of one Recommended row, when the row is scrolled to its end. Capped at [VideoPaging.MAX_ROW_PAGES] pages. */
    fun loadMoreRecommended(index: Int) {
        val row = rows.value.getOrNull(index) ?: return
        if (row.loading) return
        if (!VideoPaging.canLoadMore(row.page, VideoPaging.MAX_ROW_PAGES, row.hasMore, videos.quotaUsedUnits())) return
        setRow(row.query) { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val next = videos.searchPage(row.query, "", row.page + 1)
                setRow(row.query) { it.copy(loading = false, page = row.page + 1, hasMore = next.hasMore, videos = (it.videos + next.videos).distinctBy { v -> v.id }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed page ends the row quietly; Recommended stays usable and "Try again" reloads it.
                setRow(row.query) { it.copy(loading = false, hasMore = false) }
            } finally {
                keyTick.update { it + 1 }
            }
        }
    }

    private fun setRow(query: String, change: (RawRow) -> RawRow) =
        rows.update { list -> list.map { if (it.query == query) change(it) else it } }

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
        rows.value = emptyList()
        recommended.value = RecommendedUi.Idle
        raw.update { it.copy(results = null, searchedFor = null) }
        onDone()
    }

    private fun messageOf(e: Exception): String = when (e) {
        is VideoError -> e.message.orEmpty()
        else -> "Something went wrong reading YouTube's answer. Try again in a moment."
    }
}
