package com.zeroindex.intake.data.training

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The person's own photo of a custom exercise. The picked or captured picture is scaled to 1024 px on
 * the long edge, turned upright from its EXIF tag, and re-encoded as a JPEG (which drops the GPS position
 * and device details), then kept in the app-private `photos` folder. Only the file name is stored on the
 * exercise row. Photos are not part of backup or sync (docs/PRIVACY.md). Nothing here touches the network.
 */
@Singleton
class ExercisePhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val LONG_EDGE = 1024
        const val QUALITY = 85
    }

    /** Stores the picture at [uri] and returns its file name, or null if it is not a readable image. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
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
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "exercise-${UUID.randomUUID()}.jpg")
            file.outputStream().use { ready.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (ready !== decoded) decoded.recycle()
            ready.recycle()
            file.name
        }.getOrNull()
    }

    /** An empty file in the app cache for the camera app to write to. */
    fun newCaptureFile(): File =
        File(context.cacheDir, "capture").apply { mkdirs() }.let { File(it, "${UUID.randomUUID()}.jpg") }
}
