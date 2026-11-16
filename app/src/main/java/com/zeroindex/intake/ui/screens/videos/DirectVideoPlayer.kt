package com.zeroindex.intake.ui.screens.videos

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zeroindex.intake.domain.videos.PlayerKind

/**
 * The in-app player (AndroidX Media3 ExoPlayer) for a downloaded file or a yt-dlp stream. It has no ads because it plays the
 * file itself. [location] is a file path or a stream address and is never logged. [playing] lets a pager play only the page in
 * view; [loop] repeats (Shorts).
 */
@Composable
fun DirectVideoPlayer(
    kind: PlayerKind,
    location: String,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    loop: Boolean = false,
    startSec: Int = 0,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember(location) {
        ExoPlayer.Builder(context).build().apply {
            val uri = if (kind == PlayerKind.OFFLINE_FILE) Uri.fromFile(java.io.File(location)) else Uri.parse(location)
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // Pause when the screen goes to the background.
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }
    LaunchedEffect(player, startSec) { if (startSec > 0) { player.seekTo(startSec * 1000L); player.playWhenReady = true } }
    AndroidView(
        factory = { c -> PlayerView(c).apply { this.player = player; useController = true } },
        update = { it.player = player },
        modifier = modifier.background(Color.Black),
    )
}
