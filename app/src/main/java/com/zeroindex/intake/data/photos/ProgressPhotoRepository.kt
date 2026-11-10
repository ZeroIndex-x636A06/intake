package com.zeroindex.intake.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.zeroindex.intake.data.db.dao.ProgressPhotoDao
import com.zeroindex.intake.data.db.entity.ProgressPhotoEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.photos.PhotoAngle
import com.zeroindex.intake.domain.photos.PhotoMeta
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Progress photos: metadata in the `progress_photo` table (synced and exported as metadata only) and the image
 * itself in app-private storage. An image is scaled to 1600 px on the long edge, turned upright from its EXIF tag and
 * re-encoded as JPEG, which also drops the GPS position and device details. Nothing here touches the network, and
 * the files are never uploaded or synced. They are exported only to a zip the person asks for, when the opt-in
 * switch (Settings > Sync and export) is on.
 */
@Singleton
class ProgressPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProgressPhotoDao,
    private val clock: Clock,
) {
    companion object {
        const val LONG_EDGE = 1600
        const val QUALITY = 88
        private const val DIR = "progress_photos"
    }

    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun observe(): Flow<List<PhotoMeta>> = dao.observeAll().map { rows -> rows.map { PhotoMeta(it.id, it.takenAt, it.angle, it.fileName) } }

    fun observeNewestAt(): Flow<Long?> = dao.observeNewestAt()

    /** The stored image, or null when this phone does not have it (a row that arrived by sync, or a cleared folder). */
    fun fileOf(meta: PhotoMeta): File? = File(dir, meta.fileName).takeIf { it.isFile }

    /** An empty file in the app cache for the camera app to write to (served by the FileProvider's `capture` path). */
    fun newCaptureFile(): File =
        File(context.cacheDir, "capture").apply { mkdirs() }.let { File(it, "progress-${newId()}.jpg") }

    /**
     * Stores the picture at [uri] for [angle] on the day of [takenAt] and returns true, or false when it is not a
     * readable image. A second photo of the same angle on the same day is kept as another row; the timeline shows
     * the newest.
     */
    suspend fun add(uri: Uri, angle: PhotoAngle, takenAt: Long = clock.nowMillis()): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_EDGE) sample *= 2
            val decoded = resolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("undecodable")
            val rotation = resolver.openInputStream(uri).use { s -> s?.let { ExifInterface(it).rotationDegrees } ?: 0 }
            val longEdge = max(decoded.width, decoded.height)
            val scale = if (longEdge > LONG_EDGE) LONG_EDGE.toFloat() / longEdge else 1f
            val matrix = Matrix().apply {
                postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            val ready = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val id = newId()
            val file = File(dir, "$id.jpg")
            file.outputStream().use { ready.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (ready !== decoded) decoded.recycle()
            ready.recycle()
            val now = clock.nowMillis()
            dao.upsert(ProgressPhotoEntity(id = id, takenAt = takenAt, angle = angle, fileName = file.name, createdAt = now, updatedAt = now))
            true
        }.getOrDefault(false)
    }

    /** Soft delete: the row is hidden, nothing is removed from disk. */
    suspend fun hide(id: String) = dao.softDelete(id, clock.nowMillis())

    /** How many image files are on this phone, for the export line. */
    suspend fun fileCount(): Int = withContext(Dispatchers.IO) { dir.listFiles()?.count { it.isFile } ?: 0 }

    /** Writes every image file into a zip on [out]. Returns the number of files written. Only called on request. */
    suspend fun writeZip(out: OutputStream): Int = withContext(Dispatchers.IO) {
        var n = 0
        ZipOutputStream(out.buffered()).use { zip ->
            dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                n++
            }
        }
        n
    }
}
