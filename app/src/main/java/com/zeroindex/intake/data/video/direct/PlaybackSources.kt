package com.zeroindex.intake.data.video.direct

import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.videos.DirectPlayback
import com.zeroindex.intake.domain.videos.PlayerKind
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * What a player should play. [location] is a file path ([PlayerKind.OFFLINE_FILE]) or a stream address
 * ([PlayerKind.DIRECT_STREAM]); it is handed to the player and never logged. [note] says why a video fell back to YouTube's player.
 */
data class ResolvedSource(val kind: PlayerKind, val location: String? = null, val note: String? = null)

/**
 * Chooses and resolves the player source for a video (downloaded file, yt-dlp stream, or YouTube's own player), and holds the
 * Shorts list of the screen the person came from so the Shorts player can swipe through it. All in memory.
 */
@Singleton
class PlaybackSources @Inject constructor(
    private val settings: SettingsRepository,
    private val downloads: VideoDownloads,
    private val engine: YtDlpEngine,
    private val clock: Clock,
) {
    /** The Shorts shown in the list the person opened a Short from. */
    @Volatile var shortsInView: List<String> = emptyList()

    private val streams = ConcurrentHashMap<String, Pair<String, Long>>()

    suspend fun resolve(videoId: String): ResolvedSource {
        val file = downloads.fileFor(videoId)
        val optedIn = settings.settings.first().directVideos
        return when (DirectPlayback.choose(optedIn, file != null)) {
            PlayerKind.OFFLINE_FILE -> ResolvedSource(PlayerKind.OFFLINE_FILE, file!!.absolutePath)
            PlayerKind.YOUTUBE -> ResolvedSource(PlayerKind.YOUTUBE)
            PlayerKind.DIRECT_STREAM -> try {
                ResolvedSource(PlayerKind.DIRECT_STREAM, stream(videoId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResolvedSource(PlayerKind.YOUTUBE, note = e.message ?: "Direct playback did not work, so YouTube's player is used.")
            }
        }
    }

    private suspend fun stream(videoId: String): String {
        val now = clock.nowMillis()
        streams[videoId]?.let { (url, at) -> if (now - at < STREAM_TTL_MS) return url }
        val url = engine.streamUrl(videoId)
        streams[videoId] = url to now
        return url
    }

    private companion object {
        /** Stream addresses expire; one is reused for half an hour. */
        const val STREAM_TTL_MS = 30 * 60 * 1000L
    }
}
