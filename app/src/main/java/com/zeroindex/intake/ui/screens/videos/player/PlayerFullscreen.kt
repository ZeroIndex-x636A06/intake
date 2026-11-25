package com.zeroindex.intake.ui.screens.videos.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** What a player puts on the fullscreen layer: how to leave, and what to draw. */
@Stable
class FullscreenEntry(val onExit: () -> Unit, val content: @Composable () -> Unit)

/**
 * Where fullscreen video is drawn: a layer over the whole app, in the activity's own window. (It used to be a Dialog. A Dialog
 * is a second window: adding it took window focus from the activity, and when the rotation, the new window and a second video
 * view all landed in one slow frame the activity could not acknowledge the focus change in time, which Android reports as
 * "Input dispatching timed out". See docs/QA-LOG.md, Q12-2.)
 *
 * At most one entry at a time. [leave] only clears the entry it is given, so a player that is going away cannot clear the
 * entry of the player that replaced it.
 */
@Stable
class FullscreenHost {
    var entry: FullscreenEntry? by mutableStateOf(null)
        private set

    val active: Boolean get() = entry != null

    fun enter(next: FullscreenEntry) {
        entry = next
    }

    fun leave(previous: FullscreenEntry) {
        if (entry === previous) entry = null
    }
}

/** Provided once by the app shell. Null where there is no layer (a preview or a test): then fullscreen is simply not offered. */
val LocalFullscreenHost = staticCompositionLocalOf<FullscreenHost?> { null }

/**
 * Draws the fullscreen entry, if there is one, over everything below it. While it is there the system bars are hidden and the
 * screen is landscape; both are set once when it appears and put back once when it goes. Back leaves fullscreen.
 */
@Composable
fun FullscreenLayer(host: FullscreenHost, modifier: Modifier = Modifier) {
    val entry = host.entry ?: return
    FullscreenWindowState()
    BackHandler(onBack = entry.onExit)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // Nothing under the layer sees a touch.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        entry.content()
    }
}

/** Landscape and no system bars while this is in the composition. Runs once on entering and once on leaving, never per frame. */
@Composable
private fun FullscreenWindowState() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        val window = activity?.window
        val before = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = before
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
