package com.zeroindex.intake.ui.screens.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import androidx.lifecycle.SavedStateHandle
import com.zeroindex.intake.data.video.VideoRepository
import com.zeroindex.intake.data.video.direct.PlaybackSources
import com.zeroindex.intake.data.video.direct.ResolvedSource
import com.zeroindex.intake.domain.videos.PlayerKind
import com.zeroindex.intake.domain.videos.ShortsQueue
import com.zeroindex.intake.domain.videos.VideoMeta
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One page of the Shorts player: the details (when known) and how it will play. */
data class ShortPage(val meta: VideoMeta? = null, val source: ResolvedSource? = null)

data class ShortsUiState(val playlist: List<String>, val start: Int, val pages: Map<String, ShortPage> = emptyMap())

/** The Shorts player's list: the Shorts of the screen the person came from, starting at the one they opened. */
@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val videos: VideoRepository,
    private val sources: PlaybackSources,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val startId: String = savedStateHandle.toRoute<Route.VideoDetail>().videoId
    private val playlist = ShortsQueue.playlist(sources.shortsInView, startId)

    private val _state = MutableStateFlow(ShortsUiState(playlist, ShortsQueue.indexOf(playlist, startId)))
    val state: StateFlow<ShortsUiState> = _state.asStateFlow()

    private val requested = mutableSetOf<String>()

    /** Loads a page and the ones next to it, so a swipe lands on a video that is ready. */
    fun prepare(index: Int) {
        listOf(index, index + 1).mapNotNull { playlist.getOrNull(it) }.forEach(::load)
    }

    private fun load(id: String) {
        if (!requested.add(id)) return
        viewModelScope.launch {
            val meta = try { videos.video(id) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            _state.update { it.copy(pages = it.pages + (id to ShortPage(meta = meta))) }
            val source = sources.resolve(id)
            _state.update { s -> s.copy(pages = s.pages + (id to ShortPage(meta = s.pages[id]?.meta ?: meta, source = source))) }
        }
    }
}

/**
 * The Shorts player: a vertical, full-height page per Short. Swipe up for the next Short in the list the person came from,
 * down for the one before. A Short plays in the in-app player (looping) when direct playback is on or the video is downloaded,
 * and in YouTube's own player otherwise.
 */
@Composable
fun ShortsPlayerScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier, viewModel: ShortsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = state.start) { state.playlist.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { viewModel.prepare(it) }
    }
    VerticalPager(
        state = pager,
        modifier = modifier.fillMaxSize().background(Color.Black).padding(contentPadding),
        beyondViewportPageCount = 0,
    ) { index ->
        val id = state.playlist[index]
        val page = state.pages[id]
        val current = pager.currentPage == index
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val source = page?.source
            when {
                source == null -> CircularProgressIndicator(color = Color.White)
                source.kind == PlayerKind.YOUTUBE && YoutubeEmbed.isValidId(id) ->
                    if (current) YoutubePlayer(videoId = id, startSec = 0, thumbnailUrl = page.meta?.thumbnailUrl, durationSec = page.meta?.durationSec ?: 0)
                    else CircularProgressIndicator(color = Color.White)
                source.location != null -> DirectVideoPlayer(kind = source.kind, location = source.location, modifier = Modifier.fillMaxSize(), playing = current, loop = true)
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(page?.meta?.title ?: "YouTube Short", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2)
                val channel = page?.meta?.channelTitle.orEmpty()
                if (channel.isNotEmpty()) Text(channel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                source?.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)) }
                if (state.playlist.size > 1) Text("Swipe up for the next Short", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
