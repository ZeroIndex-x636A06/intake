package com.zeroindex.intake.data.ai.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A photo ready to send: small JPEG bytes plus where the kept copy lives. */
class PreparedPhoto(val jpeg: ByteArray, val savedPath: String)

/**
 * SPEC §6.2: never send a full-resolution image. Scaled to 1024 px on the
 * long edge at JPEG quality 85.
 *
 * Re-encoding from a Bitmap also drops every EXIF tag, so the GPS position
 * and device details a phone camera embeds never leave the phone - only the
 * pixels do. The orientation tag is applied to the pixels first, so nothing
 * arrives sideways.
 */
@Singleton
class PhotoPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val LONG_EDGE = 1024
        const val QUALITY = 85
    }

    suspend fun prepare(uri: Uri): PreparedPhoto = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file is not an image." }

        // Decode at the smallest power-of-two that is still at least the
        // target size, then scale the rest of the way exactly.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("The image could not be decoded.")

        val rotation = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).rotationDegrees } ?: 0
        }
        val longEdge = max(decoded.width, decoded.height)
        val scale = if (longEdge > LONG_EDGE) LONG_EDGE.toFloat() / longEdge else 1f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val ready = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val bytes = ByteArrayOutputStream().use { out ->
            ready.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        if (ready !== decoded) decoded.recycle()
        ready.recycle()

        // SPEC §6.8: the photo stays with the log entry for later review.
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
        PreparedPhoto(bytes, file.absolutePath)
    }

    /** Where a camera capture is written before it is prepared. */
    fun newCaptureFile(): File =
        File(context.cacheDir, "capture").apply { mkdirs() }.let { File(it, "${UUID.randomUUID()}.jpg") }
}
