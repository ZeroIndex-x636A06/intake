package com.zeroindex.intake.data.video.direct

import android.content.Context
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.videos.Transcript
import com.zeroindex.intake.domain.videos.TranscriptParser
import com.zeroindex.intake.domain.videos.YoutubeIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** What "Get transcript" gave, in words the screen can show. */
sealed interface TranscriptResult {
    data class Found(val transcript: Transcript, val fromCache: Boolean) : TranscriptResult

    /** Settings > Videos "direct playback" is off, so yt-dlp is not allowed to run. */
    data object NotEnabled : TranscriptResult

    /** The video has no captions in English or the device language. */
    data object NoCaptions : TranscriptResult

    data class Failed(val message: String) : TranscriptResult
}

/**
 * Captions of a video, fetched with yt-dlp (opt-in, Settings > Videos) on the person's tap and kept per video id in
 * app-private storage (`files/video_transcripts/<id>.json`). No YouTube API key is used and nothing is sent anywhere but
 * the request yt-dlp itself makes to YouTube. Cached files are deleted after 30 days, like the other video data; they are
 * device-local and not part of an export or sync.
 */
@Singleton
class TranscriptRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: YtDlpEngine,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val dir: File get() = File(context.filesDir, "video_transcripts")
    private val work: File get() = File(context.cacheDir, "caption_work")

    suspend fun directOn(): Boolean = settings.settings.first().directVideos

    /** A transcript fetched before, or null. Works with no network and with the option off. */
    suspend fun cached(videoId: String): Transcript? = withContext(Dispatchers.IO) {
        if (!validId(videoId)) return@withContext null
        val file = File(dir, "$videoId.json")
        if (!file.isFile) return@withContext null
        if (clock.nowMillis() - file.lastModified() > MAX_AGE_MS) {
            runCatching { file.delete() }
            return@withContext null
        }
        Transcript.decode(file.readText())?.takeIf { !it.isEmpty }
    }

    /** Fetches the captions now (the person tapped "Get transcript"). Needs the opt-in; never runs on its own. */
    suspend fun fetch(videoId: String): TranscriptResult {
        if (!validId(videoId)) return TranscriptResult.Failed("That is not a YouTube video.")
        cached(videoId)?.let { return TranscriptResult.Found(it, fromCache = true) }
        if (!directOn()) return TranscriptResult.NotEnabled
        return try {
            val file = engine.captions(videoId, Locale.getDefault().language, work)
                ?: return TranscriptResult.NoCaptions
            val segments = TranscriptParser.parse(file.text)
            if (segments.isEmpty()) return TranscriptResult.NoCaptions
            val transcript = Transcript(videoId, file.language, file.auto, segments)
            store(transcript)
            TranscriptResult.Found(transcript, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DirectVideoException) {
            TranscriptResult.Failed(e.message ?: "Could not get the captions.")
        } catch (e: Exception) {
            TranscriptResult.Failed("Could not get the captions. Check your connection and try again.")
        }
    }

    private suspend fun store(t: Transcript) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        File(dir, "${t.videoId}.json").writeText(t.encode())
        val now = clock.nowMillis()
        dir.listFiles()?.filter { now - it.lastModified() > MAX_AGE_MS }?.forEach { runCatching { it.delete() } }
    }

    private fun validId(id: String) = YoutubeIds.parse(YoutubeIds.watchUrl(id)) == id

    private companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
