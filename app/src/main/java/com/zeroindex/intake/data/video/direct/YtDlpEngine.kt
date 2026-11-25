package com.zeroindex.intake.data.video.direct

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.zeroindex.intake.domain.videos.DirectFailure
import com.zeroindex.intake.domain.videos.TranscriptLanguage
import com.zeroindex.intake.domain.videos.YoutubeIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why direct playback or a download did not work, in plain words. Messages never carry a stream address. */
class DirectVideoException(message: String, val failure: DirectFailure = DirectFailure.EXTRACTION) : Exception(message)

/** A caption file read from yt-dlp: its text (json3 or WebVTT), its language code and whether YouTube made it automatically. */
class CaptionFile(val text: String, val language: String, val auto: Boolean)

/**
 * The only place the app talks to yt-dlp (the youtubedl-android library). This is opt-in (Settings > Videos) and is not an
 * official YouTube feature; see docs/VIDEOS.md and docs/PRIVACY.md.
 *
 * Rules kept here: stream addresses and yt-dlp's output lines are never logged, never put in an exception message and never
 * stored; only a progress number leaves [download]. Formats are limited to ready-made files (audio and video in one), so
 * ffmpeg is not needed.
 */
@Singleton
class YtDlpEngine @Inject constructor(@ApplicationContext private val context: Context) {
    private val initLock = Mutex()
    private var ready = false
    private var updatedThisRun = false

    private suspend fun ensureInit() = withContext(Dispatchers.IO) {
        initLock.withLock {
            if (!ready) {
                try {
                    YoutubeDL.getInstance().init(context)
                } catch (e: Exception) {
                    throw DirectVideoException("The downloader could not start on this phone.")
                }
                ready = true
            }
        }
    }

    /** True when the phone has a network that can reach the internet (not that it is working: a timeout covers that). */
    fun hasNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Asks yt-dlp for its own newer version once per run, because YouTube changes often. Failure is not an error. */
    private suspend fun updateOnce() = withContext(Dispatchers.IO) {
        if (updatedThisRun) return@withContext
        updatedThisRun = true
        runCatching { YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE) }
    }

    /** The address of a single playable file (about 360p to 720p) for [videoId]. Held in memory by the caller, never logged. */
    suspend fun streamUrl(videoId: String): String {
        require(YoutubeIds.parse(YoutubeIds.watchUrl(videoId)) == videoId)
        // No connection: say so at once instead of starting the downloader and waiting for it to give up.
        if (!hasNetwork()) throw DirectVideoException(DirectFailure.NO_NETWORK.playbackNote(), DirectFailure.NO_NETWORK)
        ensureInit()
        return withContext(Dispatchers.IO) {
            try {
                firstUrl(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A connection failure will not be mended by an update; anything else is usually that YouTube changed:
                // update the downloader once and try again.
                val first = DirectFailure.classify(hasNetwork(), *DirectFailure.messagesOf(e))
                if (first == DirectFailure.NO_NETWORK) throw DirectVideoException(first.playbackNote(), first)
                updateOnce()
                try {
                    firstUrl(videoId)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    val f = DirectFailure.classify(hasNetwork(), *DirectFailure.messagesOf(e2))
                    throw DirectVideoException(f.playbackNote(), f)
                }
            }
        }
    }

    /**
     * The captions of [videoId] without downloading the video and without any API key. Manual captions are tried first, then
     * YouTube's automatic ones; English first, then [deviceLanguage]. Returns null when the video has none in those languages.
     * Files are written to [workDir], read, and deleted. yt-dlp's output lines are never logged.
     */
    suspend fun captions(videoId: String, deviceLanguage: String, workDir: File): CaptionFile? {
        require(YoutubeIds.parse(YoutubeIds.watchUrl(videoId)) == videoId)
        if (!hasNetwork()) throw DirectVideoException(DirectFailure.NO_NETWORK.captionsMessage(), DirectFailure.NO_NETWORK)
        ensureInit()
        return withContext(Dispatchers.IO) {
            workDir.mkdirs()
            try {
                try {
                    captionsOnce(videoId, deviceLanguage, workDir)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val first = DirectFailure.classify(hasNetwork(), *DirectFailure.messagesOf(e))
                    if (first == DirectFailure.NO_NETWORK) throw DirectVideoException(first.captionsMessage(), first)
                    updateOnce()
                    try {
                        captionsOnce(videoId, deviceLanguage, workDir)
                    } catch (e2: CancellationException) {
                        throw e2
                    } catch (e2: Exception) {
                        val f = DirectFailure.classify(hasNetwork(), *DirectFailure.messagesOf(e2))
                        throw DirectVideoException(f.captionsMessage(), f)
                    }
                }
            } finally {
                workDir.listFiles { f -> f.name.startsWith("$videoId.") }?.forEach { runCatching { it.delete() } }
            }
        }
    }

    private fun captionsOnce(videoId: String, deviceLanguage: String, dir: File): CaptionFile? {
        val langs = TranscriptLanguage.requested(deviceLanguage).joinToString(",") { "$it.*" }
        for (auto in listOf(false, true)) {
            dir.listFiles { f -> f.name.startsWith("$videoId.") }?.forEach { runCatching { it.delete() } }
            val request = YoutubeDLRequest(YoutubeIds.watchUrl(videoId))
                .addOption("--skip-download")
                .addOption("--no-playlist")
                .addOption(if (auto) "--write-auto-subs" else "--write-subs")
                .addOption("--sub-langs", langs)
                .addOption("--sub-format", "json3/vtt")
                .addOption("-o", File(dir, "$videoId.%(ext)s").absolutePath)
            YoutubeDL.getInstance().execute(request)
            val files = dir.listFiles { f -> f.name.startsWith("$videoId.") && (f.name.endsWith(".json3") || f.name.endsWith(".vtt")) }.orEmpty()
            val byLang = files.groupBy { it.name.removePrefix("$videoId.").substringBeforeLast('.') }
            val lang = TranscriptLanguage.pick(byLang.keys.toList(), deviceLanguage) ?: continue
            val file = byLang.getValue(lang).let { fs -> fs.firstOrNull { it.name.endsWith(".json3") } ?: fs.first() }
            return CaptionFile(file.readText(), lang, auto)
        }
        return null
    }

    private fun firstUrl(videoId: String): String {
        val request = YoutubeDLRequest(YoutubeIds.watchUrl(videoId)).addOption("-f", FORMAT).addOption("--no-playlist")
        return YoutubeDL.getInstance().getInfo(request).url?.takeIf { it.isNotBlank() }
            ?: throw DirectVideoException("No playable file was found.")
    }

    /**
     * Downloads [videoId] into [dir] as `<id>.<ext>` and returns the file. [onProgress] gets 0 to 100. [processId] lets
     * [cancel] stop it. Partial files are removed on failure or cancel.
     */
    suspend fun download(videoId: String, dir: File, processId: String, onProgress: (Float) -> Unit): File {
        ensureInit()
        return withContext(Dispatchers.IO) {
            dir.mkdirs()
            fun attempt() {
                val request = YoutubeDLRequest(YoutubeIds.watchUrl(videoId))
                    .addOption("-f", FORMAT)
                    .addOption("--no-playlist")
                    .addOption("--no-mtime")
                    .addOption("-o", File(dir, "$videoId.%(ext)s").absolutePath)
                YoutubeDL.getInstance().execute(request, processId) { progress, _, _ -> onProgress(progress.coerceIn(0f, 100f)) }
            }
            try {
                try {
                    attempt()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: com.yausername.youtubedl_android.YoutubeDL.CanceledException) {
                    throw e
                } catch (e: Exception) {
                    updateOnce()
                    attempt()
                }
                finished(videoId, dir) ?: throw DirectVideoException("The download finished but no file was saved.")
            } catch (e: CancellationException) {
                cleanup(videoId, dir)
                throw e
            } catch (e: com.yausername.youtubedl_android.YoutubeDL.CanceledException) {
                cleanup(videoId, dir)
                throw CancellationException("Download cancelled")
            } catch (e: DirectVideoException) {
                cleanup(videoId, dir)
                throw e
            } catch (e: Exception) {
                cleanup(videoId, dir)
                val f = DirectFailure.classify(hasNetwork(), *DirectFailure.messagesOf(e))
                throw DirectVideoException(f.downloadMessage(), f)
            }
        }
    }

    /** Stops a running download. */
    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    private fun finished(videoId: String, dir: File): File? =
        dir.listFiles { f -> f.name.startsWith("$videoId.") && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }

    private fun cleanup(videoId: String, dir: File) {
        dir.listFiles { f -> f.name.startsWith("$videoId.") }?.forEach { runCatching { it.delete() } }
    }

    private companion object {
        /** One file with sound and picture, mp4 first, up to 720p: no merging, so no ffmpeg. */
        const val FORMAT = "best[ext=mp4][height<=720]/best[height<=720]/best"
    }
}
