package com.zeroindex.intake.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.training.ExerciseGuideIndex
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where an exercise's pictures come from: the bundled Free Exercise DB photos (two per exercise, start
 * and end, found through the index) or the person's own photo for a custom exercise, a file in the
 * app's private `photos` folder. An exercise with neither shows only the muscle map.
 */
object ExerciseImages {
    private var indexCache: ExerciseGuideIndex? = null
    private val bitmaps = object : LruCache<String, ImageBitmap>(16 * 1024) {
        // Sized in KB: about 0.7 MB for a full photo, so this holds a screenful of thumbnails.
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4) / 1024
    }

    @Synchronized
    fun index(context: Context): ExerciseGuideIndex = indexCache ?: run {
        val text = runCatching {
            context.assets.open(ExerciseGuideIndex.ASSET_INDEX).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        ExerciseGuideIndex.parse(text).also { indexCache = it }
    }

    fun ownPhoto(context: Context, name: String?): File? {
        if (name.isNullOrBlank() || '/' in name || '\\' in name || name.startsWith(".")) return null
        return File(File(context.filesDir, "photos"), name).takeIf { it.isFile }
    }

    /** How many pictures this exercise has: 2 bundled, 1 of the person's own, 0 for none. */
    fun count(context: Context, exerciseId: String, photoPath: String?): Int = when {
        ownPhoto(context, photoPath) != null -> 1
        index(context).has(exerciseId) -> 2
        else -> 0
    }

    /** Decodes picture [which], at most about [maxEdge] px on the long side. */
    suspend fun load(context: Context, exerciseId: String, photoPath: String?, which: Int, maxEdge: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val own = ownPhoto(context, photoPath)
            val key = "${own?.name ?: exerciseId}:$which:$maxEdge"
            bitmaps.get(key)?.let { return@withContext it }
            val bitmap = runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                fun open() = when {
                    own != null -> if (which == 0) own.inputStream() else null
                    index(context).has(exerciseId) -> context.assets.open(ExerciseGuideIndex.photoAsset(exerciseId, which))
                    else -> null
                }
                open()?.use { BitmapFactory.decodeStream(it, null, options) }
                var sample = 1
                while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= maxEdge) sample *= 2
                open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }.getOrNull()?.asImageBitmap()
            if (bitmap != null) bitmaps.put(key, bitmap)
            bitmap
        }
}

/** One picture of an exercise, or nothing while it loads or if there is none. */
@Composable
fun ExercisePicture(
    exerciseId: String,
    photoPath: String?,
    which: Int,
    modifier: Modifier = Modifier,
    maxEdge: Int = 480,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, exerciseId, photoPath, which, maxEdge) {
        value = ExerciseImages.load(context, exerciseId, photoPath, which, maxEdge)
    }
    image?.let {
        Image(bitmap = it, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
    }
}

/** A small square picture for a list row; a quiet dumbbell stands in when there is no photo. */
@Composable
fun ExerciseThumb(exerciseId: String, photoPath: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val context = LocalContext.current
    val has = remember(exerciseId, photoPath) { ExerciseImages.count(context, exerciseId, photoPath) > 0 }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (has) {
            ExercisePicture(exerciseId, photoPath, which = 0, maxEdge = 160, modifier = Modifier.size(size))
        } else {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/**
 * The start and end photos. Auto mode crossfades between them; it stops on the start photo under
 * the reduce-motion setting. Side by side shows both at once.
 */
@Composable
fun ExercisePhotoPair(
    exerciseId: String,
    photoPath: String?,
    sideBySide: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val count = remember(exerciseId, photoPath) { ExerciseImages.count(context, exerciseId, photoPath) }
    if (count == 0) return
    val shape = RoundedCornerShape(12.dp)
    if (count == 1) {
        ExercisePicture(
            exerciseId, photoPath, 0, maxEdge = 960, contentScale = ContentScale.Fit,
            contentDescription = "Your photo of this exercise",
            modifier = modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }
    if (sideBySide) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1).forEach { i ->
                ExercisePicture(
                    exerciseId, photoPath, i,
                    contentDescription = if (i == 0) "Start position" else "End position",
                    modifier = Modifier.weight(1f).aspectRatio(3f / 2f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        return
    }
    val reduce = LocalReduceMotion.current
    val fade = if (reduce) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "exercise-crossfade")
        val a by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 3200
                    0f at 0 using LinearEasing
                    0f at 900 using LinearEasing
                    1f at 1500 using LinearEasing
                    1f at 2400 using LinearEasing
                    0f at 3000 using LinearEasing
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "fade",
        )
        a
    }
    Box(modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        ExercisePicture(exerciseId, photoPath, 0, contentDescription = "Start position", modifier = Modifier.matchParentSize())
        ExercisePicture(
            exerciseId, photoPath, 1, contentDescription = "End position",
            modifier = Modifier.matchParentSize().alpha(fade),
        )
    }
}
