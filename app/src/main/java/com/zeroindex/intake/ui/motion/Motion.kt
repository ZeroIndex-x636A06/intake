package com.zeroindex.intake.ui.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when the system animation scale is 0 (Developer options "Animator duration scale: off",
 * or "Remove animations" in accessibility settings). Transitions then use [EnterTransition.None]
 * and springs become snaps. Provided once in IntakeApp; defaults to false so previews and tests
 * animate normally.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Motion tokens: durations, easings, springs and the two navigation transitions. Kept free of
 * screen knowledge so a later polish pass can reuse it.
 */
object Motion {
    // Durations in milliseconds (Material 3 motion scale, trimmed to the ones the app uses).
    const val DurationShort = 100
    const val DurationMedium = 250
    const val DurationLong = 350

    /** Pressed buttons and tappable cards shrink to this scale. */
    const val PressScale = 0.97f

    /** Fade-through: the outgoing screen leaves quickly, the incoming one arrives after it. */
    const val FadeThroughOut = 90
    const val FadeThroughIn = 210

    // Easings.
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** The sliding highlight of the floating pill: a lively spring that settles with a small overshoot. */
    const val PillDamping = 0.72f
    const val PillStiffness = 420f

    /** Shared-axis X travels one eighth of the screen width, while fading. */
    const val SharedAxisFraction = 8

    fun <T> pillSpring(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = PillDamping, stiffness = PillStiffness)

    fun <T> standardTween(reduce: Boolean, durationMillis: Int = DurationMedium): FiniteAnimationSpec<T> =
        if (reduce) snap() else tween(durationMillis, easing = Standard)

    // Fade-through, used when switching between the tabs: a plain cross-fade, no movement and no scale (IA v5 2b).
    fun fadeThroughIn(reduce: Boolean): EnterTransition =
        fadeIn(animationSpec = tween(if (reduce) DurationShort else TabFade, easing = Standard))

    fun fadeThroughOut(reduce: Boolean): ExitTransition =
        fadeOut(animationSpec = tween(if (reduce) DurationShort else TabFade, easing = Standard))

    /** A tab switch cross-fades for 150 ms. */
    const val TabFade = 150

    /** A push slides in 300 ms; a pop (back) takes 250 ms. The screen underneath travels a quarter of its width. */
    const val PushMillis = 300
    const val PopMillis = 250
    const val UnderFraction = 4

    // Push and pop (IA v5 2b): horizontal slide only. No shrink, no fade of the screen underneath.
    // With reduce motion on, every case is a 100 ms cross-fade.
    fun pushEnter(reduce: Boolean): EnterTransition =
        if (reduce) fadeIn(tween(DurationShort)) else slideInHorizontally(
            animationSpec = tween(PushMillis, easing = FastOutSlowInEasing),
            initialOffsetX = { full -> full },
        )

    fun pushExit(reduce: Boolean): ExitTransition =
        if (reduce) fadeOut(tween(DurationShort)) else slideOutHorizontally(
            animationSpec = tween(PushMillis, easing = FastOutSlowInEasing),
            targetOffsetX = { full -> -full / UnderFraction },
        )

    fun popEnter(reduce: Boolean): EnterTransition =
        if (reduce) fadeIn(tween(DurationShort)) else slideInHorizontally(
            animationSpec = tween(PopMillis, easing = FastOutSlowInEasing),
            initialOffsetX = { full -> -full / UnderFraction },
        )

    fun popExit(reduce: Boolean): ExitTransition =
        if (reduce) fadeOut(tween(DurationShort)) else slideOutHorizontally(
            animationSpec = tween(PopMillis, easing = FastOutSlowInEasing),
            targetOffsetX = { full -> full },
        )

    /** The pill leaving or arriving as a screen is pushed or popped: quick, and instant with reduce motion. */
    fun pillEnter(reduce: Boolean): EnterTransition =
        if (reduce) EnterTransition.None else slideInVertically(tween(DurationMedium, easing = Standard)) { it } +
            fadeIn(tween(DurationMedium))

    fun pillExit(reduce: Boolean): ExitTransition =
        if (reduce) ExitTransition.None else slideOutVertically(tween(PillExitMillis, easing = Standard)) { it } +
            fadeOut(tween(PillExitMillis))

    const val PillExitMillis = 140

    // Shared axis X: forward slides in from the end, back slides in from the start.
    fun sharedAxisEnter(forward: Boolean, reduce: Boolean): EnterTransition =
        if (reduce) EnterTransition.None else slideInHorizontally(
            animationSpec = tween(DurationMedium, easing = EmphasizedDecelerate),
            initialOffsetX = { full -> (if (forward) full else -full) / SharedAxisFraction },
        ) + fadeIn(tween(DurationMedium, delayMillis = FadeThroughOut, easing = Standard))

    fun sharedAxisExit(forward: Boolean, reduce: Boolean): ExitTransition =
        if (reduce) ExitTransition.None else slideOutHorizontally(
            animationSpec = tween(DurationMedium, easing = EmphasizedAccelerate),
            targetOffsetX = { full -> (if (forward) -full else full) / SharedAxisFraction },
        ) + fadeOut(tween(FadeThroughOut, easing = Standard))
}

/** Pure so a unit test can pin the rule: only an animator scale of exactly 0 turns motion off. */
fun isMotionReduced(animatorDurationScale: Float, userSetting: Boolean = false): Boolean =
    com.zeroindex.intake.domain.motion.MotionRules.reduceMotion(animatorDurationScale, userSetting)

/** Reads Settings.Global.ANIMATOR_DURATION_SCALE; a missing setting means the default of 1. */
fun readReduceMotion(context: Context): Boolean = isMotionReduced(
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
)

/** The live system value: updates if the user flips the setting while the app is open. */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(readReduceMotion(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readReduceMotion(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}
