package com.zeroindex.intake.ui.screens.videos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zeroindex.intake.data.remote.youtube.VideoError
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.video.VideoRepository
import com.zeroindex.intake.data.video.direct.DownloadState
import com.zeroindex.intake.data.video.direct.PlaybackSources
import com.zeroindex.intake.data.video.direct.ResolvedSource
import com.zeroindex.intake.data.video.direct.VideoDownloads
import com.zeroindex.intake.domain.videos.PlayerKind
import kotlinx.coroutines.flow.combine
import com.zeroindex.intake.domain.videos.Chapter
import com.zeroindex.intake.domain.videos.ChapterParser
import com.zeroindex.intake.domain.videos.VideoMeta
import com.zeroindex.intake.domain.videos.VideoVerdict
import com.zeroindex.intake.domain.videos.WorkoutVideoClassifier
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoDetailState(
    val loading: Boolean = true,
    val videoId: String = "",
    val meta: VideoMeta? = null,
    val verdict: VideoVerdict? = null,
    val chapters: List<Chapter> = emptyList(),
    val saved: Boolean = false,
    /** True when the description (and so the chapters) came from the YouTube API; a pasted link without a key has only a title. */
    val hasDescription: Boolean = false,
    /** A workout already made from this video, if any. */
    val workoutId: String? = null,
    val error: String? = null,
    /** How the video plays: YouTube's player, a downloaded file, or a yt-dlp stream (opt-in). Null while it is being worked out. */
    val source: ResolvedSource? = null,
    /** True when the opt-in (Settings > Videos) is on, so Download is offered. */
    val directOn: Boolean = false,
    val downloadedBytes: Long? = null,
    val download: DownloadState? = null,
)

/** Video detail (IA v5 7b): the player, what the filter thinks, the chapters, save, and the way to Make this a workout. */
@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val videos: VideoRepository,
    private val training: TrainingRepository,
    private val sources: PlaybackSources,
    private val downloads: VideoDownloads,
    private val settings: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val videoId: String = savedStateHandle.toRoute<Route.VideoDetail>().videoId

    private val _state = MutableStateFlow(VideoDetailState(videoId = videoId))
    val state: StateFlow<VideoDetailState> = _state.asStateFlow()

    init {
        load()
        // Download progress and the opt-in follow live.
        viewModelScope.launch {
            combine(downloads.active, settings.settings, downloads.observe()) { active, app, done ->
                Triple(active[videoId], app.directVideos, done.firstOrNull { it.videoId == videoId }?.sizeBytes)
            }.collect { (download, on, bytes) ->
                _state.update { it.copy(download = download, directOn = on, downloadedBytes = bytes) }
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            var error: String? = null
            val meta = try {
                videos.video(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e is VideoError) e.message else "Could not reach YouTube just now."
                null
            }
            val names = runCatching { training.exercises().map { it.name } }.getOrDefault(emptyList())
            _state.value = VideoDetailState(
                loading = false,
                videoId = videoId,
                meta = meta,
                verdict = meta?.let { WorkoutVideoClassifier.classify(it, names) },
                chapters = meta?.let { ChapterParser.chapters(it.description) }.orEmpty(),
                saved = videos.isSaved(videoId),
                hasDescription = !meta?.description.isNullOrBlank(),
                workoutId = videos.workoutIdFor(videoId),
                error = error,
                source = runCatching { sources.resolve(videoId) }.getOrDefault(ResolvedSource(PlayerKind.YOUTUBE)),
                directOn = _state.value.directOn,
                downloadedBytes = _state.value.downloadedBytes,
                download = _state.value.download,
            )
        }
    }

    /** Download (saved or not: it is the person's tap). Only offered with the opt-in on. */
    fun download() {
        val meta = _state.value.meta ?: VideoMeta(id = videoId, title = "YouTube video")
        downloads.start(meta)
    }

    fun cancelDownload() = downloads.cancel(videoId)

    fun toggleSave() {
        val meta = _state.value.meta ?: VideoMeta(id = videoId, title = "YouTube video")
        viewModelScope.launch {
            if (_state.value.saved) videos.unsave(videoId) else videos.save(meta)
            _state.update { it.copy(saved = !it.saved) }
        }
    }
}
