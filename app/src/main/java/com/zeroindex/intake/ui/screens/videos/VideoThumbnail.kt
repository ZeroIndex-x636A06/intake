package com.zeroindex.intake.ui.screens.videos

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.videos.YoutubeIds
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a YouTube thumbnail into memory only. A thumbnail is API data, so it is never written to disk (the 30-day rule is
 * then trivially kept), and only https pictures from YouTube's own picture hosts are fetched.
 */
private object ThumbnailCache {
    val memory = LruCache<String, ImageBitmap>(48)

    fun allowed(url: String): Boolean {
        val host = runCatching { URL(url) }.getOrNull()?.takeIf { it.protocol == "https" }?.host ?: return false
        return host.endsWith("ytimg.com") || host.endsWith("ggpht.com") || host.endsWith("youtube.com")
    }

    suspend fun load(url: String): ImageBitmap? {
        memory.get(url)?.let { return it }
        if (!allowed(url)) return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = false
                }
                try {
                    if (connection.responseCode != 200) return@runCatching null
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = BitmapFactory.Options().apply { inSampleSize = (bounds.outWidth / 640).coerceAtLeast(1) }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        if (bitmap != null) memory.put(url, bitmap)
        return bitmap
    }
}

/**
 * A 16:9 video picture with the length in the corner and a play button. While the picture loads (or when there is none, or
 * the phone is offline) a quiet striped-look placeholder stands in, as in the mockup.
 */
@Composable
fun VideoThumbnail(
    url: String?,
    durationSec: Int,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    showPlay: Boolean = true,
) {
    val image by produceState<ImageBitmap?>(null, url) { value = url?.let { ThumbnailCache.load(it) } }
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
    ) {
        image?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (showPlay) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
        if (durationSec > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Text(
                    YoutubeIds.clock(durationSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
