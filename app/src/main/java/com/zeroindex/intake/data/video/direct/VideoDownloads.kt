package com.zeroindex.intake.data.video.direct

import android.content.Context
import com.zeroindex.intake.data.db.dao.VideoDao
import com.zeroindex.intake.data.db.entity.DownloadedVideoEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.videos.VideoMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where one download is. */
sealed interface DownloadState {
    /** [percent] is 0 to 100, or -1 while yt-dlp has not reported yet. */
    data class Running(val percent: Float) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** What the Downloaded screen draws for one row. */
data class DownloadedVideo(val id: String, val videoId: String, val title: String, val channelTitle: String, val durationSec: Int?, val thumbnailUrl: String?, val sizeBytes: Long)

/**
 * Downloads made with yt-dlp (opt-in). Files live in `files/video_downloads` (app-private storage, never shared storage and
 * never in a backup); the `downloaded_video` row lists them. A download runs on the app's own scope while the app process
 * lives: if Android ends the process it stops, and the partial file is removed the next time. Delete removes the file first,
 * then soft-deletes the row (nothing is hard-deleted).
 */
@Singleton
class VideoDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VideoDao,
    private val engine: YtDlpEngine,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _active = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    /** Downloads in progress or failed this run, by video id. */
    val active: StateFlow<Map<String, DownloadState>> = _active.asStateFlow()

    val directory: File get() = File(context.filesDir, "video_downloads")

    fun observe(): Flow<List<DownloadedVideo>> = kotlinx.coroutines.flow.flow {
        dao.observeDownloaded().collect { rows ->
            emit(rows.map { DownloadedVideo(it.id, it.videoId, it.title, it.channelTitle, it.durationSec, it.thumbnailUrl, it.sizeBytes) })
        }
    }

    /** The file of a finished download of [videoId], or null (no row, or the file is gone). */
    suspend fun fileFor(videoId: String): File? = withContext(Dispatchers.IO) {
        val row = dao.downloadedByVideoId(videoId) ?: return@withContext null
        File(directory, row.fileName).takeIf { it.isFile }
    }

    /** Bytes on disk for every download, for "Storage used". */
    fun usedBytes(rows: List<DownloadedVideo>): Long = rows.sumOf { it.sizeBytes }

    fun start(meta: VideoMeta) {
        val id = meta.id
        synchronized(jobs) { if (jobs[id]?.isActive == true) return }
        _active.update { it + (id to DownloadState.Running(-1f)) }
        val job = scope.launch {
            try {
                if (fileFor(id) != null) {
                    _active.update { it - id }
                    return@launch
                }
                val file = engine.download(id, directory, processId = id) { p -> _active.update { it + (id to DownloadState.Running(p)) } }
                val now = clock.nowMillis()
                dao.upsertDownloaded(
                    DownloadedVideoEntity(
                        id = newId(), videoId = id, title = meta.title.ifBlank { "YouTube video" }, channelTitle = meta.channelTitle,
                        durationSec = meta.durationSec.takeIf { it > 0 }, thumbnailUrl = meta.thumbnailUrl, fileName = file.name,
                        sizeBytes = file.length(), createdAt = now, updatedAt = now,
                    ),
                )
                _active.update { it - id }
            } catch (e: CancellationException) {
                _active.update { it - id }
                throw e
            } catch (e: Exception) {
                _active.update { it + (id to DownloadState.Failed(e.message ?: "The download failed.")) }
            }
        }
        synchronized(jobs) { jobs[id] = job }
    }

    /** Stops a running download and removes what it had written. */
    fun cancel(videoId: String) {
        engine.cancel(videoId)
        synchronized(jobs) { jobs.remove(videoId) }?.cancel()
        _active.update { it - videoId }
    }

    fun dismissFailure(videoId: String) = _active.update { map -> if (map[videoId] is DownloadState.Failed) map - videoId else map }

    /** Delete: the file goes first, then the row is soft-deleted. */
    suspend fun delete(id: String, videoId: String) = withContext(Dispatchers.IO) {
        val row = dao.downloadedByVideoId(videoId)
        if (row != null) runCatching { File(directory, row.fileName).delete() }
        dao.softDeleteDownloaded(id, clock.nowMillis())
    }
}
