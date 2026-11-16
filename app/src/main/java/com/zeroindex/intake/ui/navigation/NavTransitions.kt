package com.zeroindex.intake.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import kotlin.math.roundToInt

/**
 * One screen's part of a navigation transition, as plain numbers so the mirror rule is tested on the JVM.
 *
 * The screen moves from [fromOffset] to [toOffset], each a fraction of the screen width (1 is one full width to
 * the right, -0.25 is a quarter of a width to the left), while its alpha goes from [fromAlpha] to [toAlpha].
 * An enter transition starts at the `from` values and an exit transition ends at the `to` values.
 */
data class NavSlide(
    val fromOffset: Float,
    val toOffset: Float,
    val fromAlpha: Float,
    val toAlpha: Float,
    val durationMillis: Int,
)

/**
 * The navigation motion, in one place. Every transition the NavHost uses (root and nested tab graphs, the
 * composables, and predictive back) comes from here, so nothing overrides it inconsistently.
 *
 * - Opening a pushed screen: it slides in from the right, over the old one. The old one moves a quarter of a
 *   width to the left and dims a little.
 * - Going back: the exact reverse. The top screen slides out to the right, and the previous screen slides back
 *   from that offset to its place.
 * - Switching tabs: a quick cross-fade with no movement.
 */
object NavSpecs {
    /** Push and pop share one duration and one easing ([NavEasing]). */
    const val SLIDE_MILLIS = 300

    /** The screen underneath sits this far to the left (a quarter of its width). */
    const val UNDER_OFFSET = -0.25f

    /** The screen underneath dims to this alpha while it is covered, and comes back up to 1 on the way back. */
    const val UNDER_ALPHA = 0.85f

    /** A tab switch cross-fades for this long. */
    const val TAB_FADE_MILLIS = 150

    /** With reduce motion on, every pushed or popped screen cross-fades for this long, with no movement. */
    const val REDUCED_MILLIS = 100

    /** The new screen arrives from the right, over the old one. */
    val push = NavSlide(fromOffset = 1f, toOffset = 0f, fromAlpha = 1f, toAlpha = 1f, durationMillis = SLIDE_MILLIS)

    /** The old screen moves left under the new one and dims. Mirror of [popIn]. */
    val pushUnder = NavSlide(
        fromOffset = 0f,
        toOffset = UNDER_OFFSET,
        fromAlpha = 1f,
        toAlpha = UNDER_ALPHA,
        durationMillis = SLIDE_MILLIS,
    )

    /** Going back: the previous screen slides in from the left and brightens back to its place. Mirror of [pushUnder]. */
    val pop = NavSlide(
        fromOffset = UNDER_OFFSET,
        toOffset = 0f,
        fromAlpha = UNDER_ALPHA,
        toAlpha = 1f,
        durationMillis = SLIDE_MILLIS,
    )

    /** Going back: the top screen slides out to the right. Mirror of [push]. */
    val popOut = NavSlide(fromOffset = 0f, toOffset = 1f, fromAlpha = 1f, toAlpha = 1f, durationMillis = SLIDE_MILLIS)
}

/** The easing for every slide, both ways. */
private val NavEasing = FastOutSlowInEasing

/** The tab cross-fade's easing. */
private val TabEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun slideEnter(slide: NavSlide): EnterTransition = slideInHorizontally(
    animationSpec = tween(slide.durationMillis, easing = NavEasing),
    initialOffsetX = { full -> (slide.fromOffset * full).roundToInt() },
) + fadeIn(animationSpec = tween(slide.durationMillis, easing = NavEasing), initialAlpha = slide.fromAlpha)

private fun slideExit(slide: NavSlide): ExitTransition = slideOutHorizontally(
    animationSpec = tween(slide.durationMillis, easing = NavEasing),
    targetOffsetX = { full -> (slide.toOffset * full).roundToInt() },
) + fadeOut(animationSpec = tween(slide.durationMillis, easing = NavEasing), targetAlpha = slide.toAlpha)

/** Opening a pushed screen. */
fun pushIn(reduce: Boolean): EnterTransition =
    if (reduce) fadeIn(tween(NavSpecs.REDUCED_MILLIS)) else slideEnter(NavSpecs.push)

/** The screen under a push. */
fun pushOut(reduce: Boolean): ExitTransition =
    if (reduce) fadeOut(tween(NavSpecs.REDUCED_MILLIS)) else slideExit(NavSpecs.pushUnder)

/** Going back: the previous screen comes back. Also the predictive-back enter. */
fun backIn(reduce: Boolean): EnterTransition =
    if (reduce) fadeIn(tween(NavSpecs.REDUCED_MILLIS)) else slideEnter(NavSpecs.pop)

/** Going back: the top screen leaves to the right. Also the predictive-back exit. */
fun backOut(reduce: Boolean): ExitTransition =
    if (reduce) fadeOut(tween(NavSpecs.REDUCED_MILLIS)) else slideExit(NavSpecs.popOut)

/** Switching tabs: the new tab cross-fades in. */
fun tabIn(reduce: Boolean): EnterTransition =
    fadeIn(tween(if (reduce) NavSpecs.REDUCED_MILLIS else NavSpecs.TAB_FADE_MILLIS, easing = TabEasing))

/** Switching tabs: the old tab cross-fades out. */
fun tabOut(reduce: Boolean): ExitTransition =
    fadeOut(tween(if (reduce) NavSpecs.REDUCED_MILLIS else NavSpecs.TAB_FADE_MILLIS, easing = TabEasing))
