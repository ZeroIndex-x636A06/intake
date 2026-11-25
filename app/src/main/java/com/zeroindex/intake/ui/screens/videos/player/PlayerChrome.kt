package com.zeroindex.intake.ui.screens.videos.player

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.domain.videos.Chapter
import com.zeroindex.intake.domain.videos.ControlsVisibility
import com.zeroindex.intake.domain.videos.LoopBounds
import com.zeroindex.intake.domain.videos.PlaybackRates
import com.zeroindex.intake.domain.videos.PlayerChapters
import com.zeroindex.intake.domain.videos.PlayerSeek
import com.zeroindex.intake.domain.videos.PlayerTime
import com.zeroindex.intake.ui.motion.rememberSystemReduceMotion
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake
import com.zeroindex.intake.ui.theme.tabular
import kotlinx.coroutines.delay

/** The fraction (0..1) the finger is dragging the scrubber to, or null. Read only by the leaf composables that show it. */
@Stable
private class ScrubState {
    var fraction by mutableStateOf<Float?>(null)
}

private data class SeekHint(val forward: Boolean, val totalSec: Int, val id: Int)

private val Overlay = Color.Black.copy(alpha = 0.55f)
private val Badge = Color.Black.copy(alpha = 0.7f)

/**
 * Intake's controls over the video (DESIGN-V6: dark overlay, white controls, the lime accent for the play button and the played
 * part of the scrubber, duration-badge style chips). Full style has everything; Shorts has tap to pause and a thin scrubber.
 * The position is read only in [ElapsedText], [RemainingText], [ChapterLabel] and the scrubber's draw block, so a tick of the
 * clock redraws those and not this function.
 */
@Composable
internal fun PlayerChrome(
    controller: PlayerController,
    chapters: List<Chapter>,
    style: PlayerStyle,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    openInYoutubeUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduce = rememberSystemReduceMotion()
    val minimal = style == PlayerStyle.Shorts
    val touchExploring = remember { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    var visible by remember { mutableStateOf(true) }
    var touchNonce by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<SeekHint?>(null) }
    val scrub = remember { ScrubState() }
    val playing = controller.isPlaying
    val error = controller.error

    // Show the controls whenever playback stops; hide them 3 s after the last touch while playing.
    LaunchedEffect(playing) { if (!playing) visible = true }
    LaunchedEffect(visible, touchNonce, playing, scrubbing, menuOpen) {
        if (!visible || touchExploring || minimal) return@LaunchedEffect
        val start = SystemClock.elapsedRealtime()
        delay(ControlsVisibility.AUTO_HIDE_MS)
        if (ControlsVisibility.shouldAutoHide(playing, scrubbing, menuOpen, SystemClock.elapsedRealtime() - start)) visible = false
    }
    LaunchedEffect(hint?.id) {
        if (hint != null) {
            delay(if (reduce) 900L else 700L)
            hint = null
        }
    }

    fun userSeek(ms: Long) {
        val l = controller.loop
        if (l != null && (ms < l.startMs || ms >= l.endMs)) controller.loop = null
        controller.seekTo(ms)
        touchNonce++
    }

    fun jump(forward: Boolean) {
        val delta = if (forward) PlayerSeek.STEP_MS else -PlayerSeek.STEP_MS
        userSeek(PlayerSeek.target(controller.positionMs, delta, controller.durationMs))
        val prev = hint
        val total = if (prev != null && prev.forward == forward) prev.totalSec + 10 else 10
        hint = SeekHint(forward, total, (prev?.id ?: 0) + 1)
    }

    Box(modifier) {
        // Taps and double-taps. It sits above the video and under the buttons.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(minimal, controller) {
                    detectTapGestures(
                        onTap = {
                            touchNonce++
                            if (minimal) controller.togglePlay() else visible = !visible
                        },
                        onDoubleTap = if (minimal) null else ({ o: Offset -> jump(o.x > size.width / 2f) }),
                    )
                }
                .semantics {
                    contentDescription = "Video player"
                    customActions = listOf(
                        CustomAccessibilityAction("Back 10 seconds") { jump(false); true },
                        CustomAccessibilityAction("Forward 10 seconds") { jump(true); true },
                        CustomAccessibilityAction(if (playing) "Pause" else "Play") { controller.togglePlay(); true },
                    )
                },
        )

        // The double-tap hint: a soft circle on the side that was tapped.
        hint?.let { h ->
            Box(
                Modifier
                    .align(if (h.forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 24.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (h.forward) IntakeIcons.FastForward else IntakeIcons.Rewind, null, tint = Color.White, modifier = Modifier.size(IconSize.Default))
                    Text("${h.totalSec} s", style = NumberSmall.copy(fontSize = 14.sp), color = Color.White)
                }
            }
        }

        // YouTube's mark stays visible: this names where the video comes from and is never hidden with the controls.
        if (controller.backend == PlayerBackend.YOUTUBE && error == null) YoutubeBadge(Modifier.align(Alignment.TopStart))

        if (error != null) {
            ErrorOverlay(error, controller, openInYoutubeUrl)
            // Drawn after the panel so the dark overlay does not dim it.
            if (controller.backend == PlayerBackend.YOUTUBE) YoutubeBadge(Modifier.align(Alignment.TopStart))
            return@Box
        }

        val showSpinner = controller.buffering && !controller.ended
        if (showSpinner) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp).semantics { contentDescription = "Buffering" },
            )
        }

        if (minimal) {
            // Shorts: a big play button only while paused, and a thin scrubber along the bottom.
            if (!playing && !showSpinner) CentreButton(controller, Modifier.align(Alignment.Center))
            Scrubber(
                controller, scrub, chapters, thin = true,
                onScrubbing = { scrubbing = it }, onSeek = ::userSeek,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
            return@Box
        }

        Fade(visible, reduce) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Overlay, Color.Transparent))))
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Overlay))))

                // Top right: speed, loop this chapter, fullscreen.
                Row(
                    Modifier.align(Alignment.TopEnd).padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedControl(controller, menuOpen, onMenu = { menuOpen = it }, onChanged = { touchNonce++ })
                    if (chapters.isNotEmpty()) {
                        val looping = controller.loop != null
                        ChromeIconButton(
                            icon = IntakeIcons.Loop,
                            description = if (looping) "Stop repeating this chapter" else "Repeat this chapter",
                            selected = looping,
                            onClick = {
                                touchNonce++
                                controller.loop = if (looping) null else {
                                    val idx = PlayerChapters.indexAt(chapters, (controller.positionMs / 1000L).toInt()).coerceAtLeast(0)
                                    LoopBounds.forChapter(chapters, idx, (controller.durationMs / 1000L).toInt())
                                }
                            },
                        )
                    }
                    ChromeIconButton(
                        icon = if (fullscreen) IntakeIcons.FullscreenExit else IntakeIcons.Fullscreen,
                        description = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                        onClick = onToggleFullscreen,
                    )
                }

                // Centre: back 10, play or pause, forward 10.
                if (!showSpinner) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ChromeIconButton(IntakeIcons.Rewind, "Back 10 seconds", onClick = { jump(false) })
                        CentreButton(controller)
                        ChromeIconButton(IntakeIcons.FastForward, "Forward 10 seconds", onClick = { jump(true) })
                    }
                }

                // Bottom: the chapter, then elapsed, scrubber, remaining.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                    ChapterLabel(controller, scrub, chapters)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ElapsedText(controller, scrub)
                        Scrubber(
                            controller, scrub, chapters, thin = false,
                            onScrubbing = { scrubbing = it }, onSeek = ::userSeek,
                            modifier = Modifier.weight(1f),
                        )
                        RemainingText(controller)
                    }
                }
            }
        }
    }
}

@Composable
private fun YoutubeBadge(modifier: Modifier = Modifier) {
    Text(
        "YouTube",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .padding(8.dp)
            .clip(IntakeShape.Pill)
            .background(Badge)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * What stays on screen when YouTube's own controls are showing (the plain-embed fallback): the YouTube mark, a way out to the
 * YouTube app or site, and a note when the frame has shown no sign of life. It never covers the middle or the bottom, where
 * YouTube's controls are, and everything but the button lets touches through to the video.
 */
@Composable
internal fun FallbackChrome(stalled: Boolean, openInYoutubeUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier) {
        YoutubeBadge(Modifier.align(Alignment.TopStart))
        if (openInYoutubeUrl != null) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open this video in YouTube") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(openInYoutubeUrl)))
                    }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clip(IntakeShape.Pill).background(Badge).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(IntakeIcons.External, contentDescription = null, tint = Color.White, modifier = Modifier.size(IconSize.Small))
                    Text("  Open in YouTube", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                }
            }
        }
        if (stalled) {
            Text(
                "YouTube's player has not started here. Open the video in YouTube instead.",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .clip(IntakeShape.Pill)
                    .background(Badge)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Fade(visible: Boolean, reduce: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduce) EnterTransition.None else fadeIn(tween(150)),
        exit = if (reduce) ExitTransition.None else fadeOut(tween(150)),
    ) { content() }
}

/** The big play, pause or replay button: the lime accent, 72 dp. */
@Composable
private fun CentreButton(controller: PlayerController, modifier: Modifier = Modifier) {
    val c = MaterialTheme.intake
    val label = when {
        controller.ended -> "Replay"
        controller.isPlaying -> "Pause"
        else -> "Play"
    }
    IconButton(
        onClick = controller::togglePlay,
        modifier = modifier.size(72.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = c.accent, contentColor = c.onAccent),
    ) {
        Icon(
            when {
                controller.ended -> IntakeIcons.History
                controller.isPlaying -> IntakeIcons.PauseFilled
                else -> IntakeIcons.PlayFilled
            },
            contentDescription = label,
            modifier = Modifier.size(IconSize.Large),
        )
    }
}

@Composable
private fun ChromeIconButton(icon: ImageVector, description: String, onClick: () -> Unit, selected: Boolean = false) {
    val c = MaterialTheme.intake
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) c.accent else Color.Transparent,
            contentColor = if (selected) c.onAccent else Color.White,
        ),
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(IconSize.Default)) }
}

@Composable
private fun SpeedControl(controller: PlayerController, open: Boolean, onMenu: (Boolean) -> Unit, onChanged: () -> Unit) {
    Box {
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Change playback speed") { onMenu(true) }
                .semantics { contentDescription = "Playback speed ${PlaybackRates.label(controller.rate)}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                PlaybackRates.label(controller.rate),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = Color.White,
                modifier = Modifier.clip(IntakeShape.Pill).background(Badge).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { onMenu(false) }) {
            PlaybackRates.ALL.forEach { r ->
                DropdownMenuItem(
                    text = { Text(PlaybackRates.label(r), style = MaterialTheme.typography.bodyMedium.tabular()) },
                    leadingIcon = {
                        if (r == PlaybackRates.nearest(controller.rate)) Icon(IntakeIcons.Check, contentDescription = "Selected", modifier = Modifier.size(IconSize.Small))
                        else Box(Modifier.size(IconSize.Small))
                    },
                    onClick = { controller.changeRate(r); onMenu(false); onChanged() },
                )
            }
        }
    }
}

@Composable
private fun ElapsedText(controller: PlayerController, scrub: ScrubState) {
    val f = scrub.fraction
    val ms = if (f != null) PlayerSeek.positionAt(f, controller.durationMs) else controller.positionMs
    Text(PlayerTime.format(ms), style = MaterialTheme.typography.labelLarge.tabular(), color = Color.White, maxLines = 1)
}

@Composable
private fun RemainingText(controller: PlayerController) {
    Text(PlayerTime.remaining(controller.positionMs, controller.durationMs), style = MaterialTheme.typography.labelLarge.tabular(), color = Color.White, maxLines = 1)
}

/** The chapter at the playhead (or the finger while scrubbing). Only changes when the chapter does. */
@Composable
private fun ChapterLabel(controller: PlayerController, scrub: ScrubState, chapters: List<Chapter>) {
    if (chapters.isEmpty()) return
    val label by remember(chapters, controller) {
        derivedStateOf {
            val f = scrub.fraction
            val ms = if (f != null) PlayerSeek.positionAt(f, controller.durationMs) else controller.positionMs
            PlayerChapters.labelAt(chapters, (ms / 1000L).toInt())
        }
    }
    val text = label ?: return
    Text(
        (if (controller.loop != null) "Repeating: " else "") + text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
    )
}

/**
 * The scrubber: a track with a tick at each chapter start, the played part in the accent colour and a thumb. Drawn in one
 * Canvas whose draw block reads the position, so the clock redraws only this. 48 dp tall to touch (24 dp when [thin]).
 */
@Composable
private fun Scrubber(
    controller: PlayerController,
    scrub: ScrubState,
    chapters: List<Chapter>,
    thin: Boolean,
    onScrubbing: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.intake.accent
    val durationSec = (controller.durationMs / 1000L).toInt()
    val ticks = remember(chapters, durationSec) { PlayerChapters.tickFractions(chapters, durationSec) }
    // A fixed height, so the touch area is this strip and never the whole player (the Canvas below used to fill the parent).
    Box(
        modifier
            .height(if (thin) 24.dp else 48.dp)
            .pointerInput(controller) {
                detectTapGestures { o -> onSeek(PlayerSeek.positionAt(o.x / size.width, controller.durationMs)) }
            }
            .pointerInput(controller) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onScrubbing(true); scrub.fraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        val f = scrub.fraction
                        scrub.fraction = null
                        onScrubbing(false)
                        if (f != null) onSeek(PlayerSeek.positionAt(f, controller.durationMs))
                    },
                    onDragCancel = { scrub.fraction = null; onScrubbing(false) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrub.fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            }
            .semantics {
                contentDescription = "Seek bar"
                stateDescription = PlayerTime.spoken(controller.positionMs, controller.durationMs)
                progressBarRangeInfo = ProgressBarRangeInfo(PlayerSeek.fractionOf(controller.positionMs, controller.durationMs), 0f..1f)
                setProgress { f -> onSeek(PlayerSeek.positionAt(f, controller.durationMs)); true }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dragging = scrub.fraction
            val frac = dragging ?: PlayerSeek.fractionOf(controller.positionMs, controller.durationMs)
            val trackH = (if (thin) 3.dp else if (dragging != null) 6.dp else 4.dp).toPx()
            val y = if (thin) size.height - trackH else (size.height - trackH) / 2f
            val corner = CornerRadius(trackH / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, trackH), corner)
            drawRoundRect(accent, Offset(0f, y), Size(size.width * frac, trackH), corner)
            val tickW = 2.dp.toPx()
            ticks.forEach { t -> drawRect(Color.Black.copy(alpha = 0.6f), Offset(size.width * t - tickW / 2f, y), Size(tickW, trackH)) }
            if (!thin) drawCircle(accent, radius = (if (dragging != null) 9.dp else 7.dp).toPx(), center = Offset(size.width * frac, y + trackH / 2f))
        }
    }
}

/** The error state: what went wrong in words, Try again (when it can help) and Open in YouTube. */
@Composable
private fun BoxScope.ErrorOverlay(error: PlayerError, controller: PlayerController, openInYoutubeUrl: String?) {
    val context = LocalContext.current
    val c = MaterialTheme.intake
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(IntakeIcons.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(IconSize.Large))
        Text(error.message, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (error.canRetry) {
                Button(
                    onClick = controller::retry,
                    shape = IntakeShape.Button,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Try again", maxLines = 1) }
            }
            if (openInYoutubeUrl != null) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(openInYoutubeUrl))) },
                    shape = IntakeShape.Button,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(IntakeIcons.External, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Text("  Open in YouTube", maxLines = 1)
                }
            }
        }
    }
}
