package com.zeroindex.intake.ui.screens.videos.player

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zeroindex.intake.domain.videos.Chapter
import com.zeroindex.intake.domain.videos.PlayerChapters
import com.zeroindex.intake.domain.videos.PlayerKind
import com.zeroindex.intake.domain.videos.YoutubeIds
import com.zeroindex.intake.ui.screens.videos.VideoThumbnail
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlinx.coroutines.delay

/** What the player plays. */
sealed interface PlayerSource {
    /** YouTube's own player (the default). */
    data class Youtube(val videoId: String) : PlayerSource

    /** A downloaded file or a yt-dlp stream, played by Media3. [location] is never logged. */
    data class Media(val kind: PlayerKind, val location: String) : PlayerSource
}

enum class PlayerStyle {
    /** Video page: 16:9, all controls, fullscreen. */
    Full,

    /** Shorts: fills its page, tap to pause, a thin scrubber. */
    Shorts,
}

/**
 * The state the page and the player share: whether playback has been started, where it is, and fullscreen. It survives
 * rotation and fullscreen (saved as plain values; the player itself is recreated at [positionMs]). The page uses
 * [seekToSec] for the chapters list and [currentChapter] to highlight the one playing.
 */
@Stable
class PlayerSession(
    started: Boolean = false,
    startMs: Long = 0L,
    resumePlaying: Boolean = true,
    fullscreen: Boolean = false,
    rate: Float = 1f,
) {
    var started by mutableStateOf(started)
        internal set
    var fullscreen by mutableStateOf(fullscreen)
        internal set
    internal var startMs by mutableLongStateOf(startMs)
    internal var resumePlaying: Boolean = resumePlaying
    internal var rate: Float = rate
    internal var controller by mutableStateOf<PlayerController?>(null)

    /** Where playback is, in ms. Read it only inside a derived state or a leaf composable: it changes four times a second. */
    val positionMs: Long get() = controller?.positionMs ?: startMs

    /** Seeks the player to [sec] and plays; starts it there if it has not started yet. */
    fun seekToSec(sec: Int) {
        val ms = sec.coerceAtLeast(0) * 1000L
        val c = controller
        if (c == null) {
            startMs = ms
            resumePlaying = true
            started = true
        } else {
            c.loop = null
            c.seekTo(ms)
            c.play()
        }
    }

    /** The index of the chapter playing now, or -1. */
    fun currentChapter(chapters: List<Chapter>): Int = PlayerChapters.playingIndex(started, chapters, (positionMs / 1000L).toInt())

    companion object {
        val Saver = listSaver<PlayerSession, Any>(
            save = { listOf(it.started, it.positionMs, it.controller?.isPlaying ?: it.resumePlaying, it.fullscreen, it.controller?.rate ?: it.rate) },
            restore = { PlayerSession(it[0] as Boolean, it[1] as Long, it[2] as Boolean, it[3] as Boolean, it[4] as Float) },
        )
    }
}

@Composable
fun rememberPlayerSession(startedInitially: Boolean = false, startSec: Int = 0): PlayerSession =
    rememberSaveable(saver = PlayerSession.Saver) {
        // A route start second above 0 (a "play from 2:15" chip) starts the player there, playing.
        PlayerSession(started = startedInitially || startSec > 0, startMs = startSec.coerceAtLeast(0) * 1000L)
    }

/**
 * Intake's own video player, used for both backends (DESIGN-V6 players): the video surface is YouTube's player or Media3, and
 * the controls on top are Intake's. Before it starts it shows the video's picture and a play button, so nothing loads until the
 * person asks. [active] false pauses it (a pager page that is not in view). [loopWhole] repeats the video (Shorts).
 */
@Composable
fun IntakeVideoPlayer(
    source: PlayerSource,
    session: PlayerSession,
    modifier: Modifier = Modifier,
    chapters: List<Chapter> = emptyList(),
    style: PlayerStyle = PlayerStyle.Full,
    thumbnailUrl: String? = null,
    durationHintSec: Int = 0,
    openInYoutubeUrl: String? = null,
    active: Boolean = true,
    loopWhole: Boolean = false,
) {
    val frame = if (style == PlayerStyle.Full) {
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(IntakeShape.Thumb)
    } else {
        modifier.fillMaxSize()
    }
    if (!session.started) {
        val poster = (source as? PlayerSource.Youtube)?.let { thumbnailUrl ?: YoutubeIds.thumbnailUrl(it.videoId) } ?: thumbnailUrl
        VideoThumbnail(
            url = poster,
            durationSec = durationHintSec,
            modifier = frame.clickable(role = Role.Button, onClickLabel = "Play the video") {
                session.resumePlaying = true
                session.started = true
            },
        )
        return
    }

    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember(source) {
        val startMs = session.startMs
        val auto = session.resumePlaying
        when (source) {
            is PlayerSource.Youtube -> YoutubePlayerController(context, source.videoId, startMs, auto, loopWhole)
            is PlayerSource.Media -> Media3PlayerController(context, source.kind, source.location, loopWhole, startMs, auto)
        }.also { c -> if (session.rate != 1f) c.changeRate(session.rate) }
    }
    DisposableEffect(controller) {
        session.controller = controller
        onDispose {
            session.startMs = controller.positionMs
            session.resumePlaying = controller.isPlaying
            session.rate = controller.rate
            if (session.controller === controller) session.controller = null
            controller.release()
        }
    }
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.onHostPause()
                Lifecycle.Event.ON_RESUME -> controller.onHostResume()
                Lifecycle.Event.ON_STOP -> controller.onHostStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // The position ticker: only while the screen is visible, and only Media3 needs it (YouTube's page pushes its time).
    LaunchedEffect(controller) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                controller.poll()
                delay(if (controller.isPlaying) 250L else 1_000L)
            }
        }
    }
    LaunchedEffect(controller, active) {
        if (!active) controller.pause() else if (session.resumePlaying) controller.play()
    }

    // Fullscreen is drawn on the app-wide layer (PlayerFullscreen.kt) in the activity's own window. The controller and its video
    // view stay the same objects: only where the view and the controls are drawn changes, so position and playing carry over.
    val host = LocalFullscreenHost.current
    val fullscreen = session.fullscreen && style == PlayerStyle.Full && host != null
    if (fullscreen && host != null) {
        DisposableEffect(host, controller, chapters, style, openInYoutubeUrl) {
            val entry = FullscreenEntry(onExit = { session.fullscreen = false }) {
                PlayerContent(controller, chapters, style, true, { session.fullscreen = false }, openInYoutubeUrl, Modifier.fillMaxSize())
            }
            host.enter(entry)
            onDispose { host.leave(entry) }
        }
        // Leaving the screen (or this player) while fullscreen ends fullscreen, so coming back does not re-enter it.
        DisposableEffect(session) { onDispose { session.fullscreen = false } }
    }
    Box(frame.background(Color.Black)) {
        if (!fullscreen) {
            PlayerContent(controller, chapters, style, false, { session.fullscreen = true }, openInYoutubeUrl, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PlayerContent(
    controller: PlayerController,
    chapters: List<Chapter>,
    style: PlayerStyle,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    openInYoutubeUrl: String?,
    modifier: Modifier,
) {
    Box(modifier.background(Color.Black)) {
        PlayerSurface(controller, Modifier.fillMaxSize())
        val chromeModifier = if (fullscreen) {
            // Keep the controls clear of a camera cutout in landscape; the video itself fills the screen.
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        } else {
            Modifier.fillMaxSize()
        }
        val youtube = controller as? YoutubePlayerController
        // In the plain-embed fallback YouTube's own controls show; Intake keeps a small chrome (badge, way out, a note) and
        // takes over fully again if the embed reports an error, so a failure is never a bare black box.
        if (youtube != null && youtube.fallbackEmbed && controller.error == null) {
            FallbackChrome(stalled = youtube.fallbackStalled, openInYoutubeUrl = openInYoutubeUrl, modifier = chromeModifier)
        } else {
            PlayerChrome(
                controller = controller,
                chapters = chapters,
                style = style,
                fullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                openInYoutubeUrl = openInYoutubeUrl,
                modifier = chromeModifier,
            )
        }
    }
}

/**
 * The video surface of either backend. Each controller owns exactly one video view (the PlayerView or the WebView); a slot only
 * holds it. Moving between the inline slot and the fullscreen layer re-parents that one view: nothing is inflated or rebuilt.
 */
@Composable
private fun PlayerSurface(controller: PlayerController, modifier: Modifier) {
    val view: View = when (controller) {
        is Media3PlayerController -> controller.playerView
        is YoutubePlayerController -> controller.webView
        else -> return
    }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            FrameLayout(c).apply {
                (view.parent as? ViewGroup)?.removeView(view)
                addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        onRelease = { slot -> if (view.parent === slot) slot.removeView(view) },
    )
}
