package com.zeroindex.intake.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.nutrition.DayProgress
import com.zeroindex.intake.ui.motion.BurstOverlay
import com.zeroindex.intake.ui.motion.CountUpText
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.motion.pulseOn
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.ProteinAccent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A fraction that sweeps from 0 to [target] the first time it shows (after [delayMillis]) and glides to
 * each new value after. With reduce-motion it is simply the target. Read the returned State inside a
 * draw or layer lambda so only the drawing is redone each frame.
 */
@Composable
fun rememberSweepFraction(target: Float, delayMillis: Int = 0): State<Float> {
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) {
            anim.snapTo(target)
        } else {
            anim.animateTo(target, tween(Motion.DurationLong + 350, delayMillis = delayMillis, easing = Motion.EmphasizedDecelerate))
        }
    }
    return anim.asState()
}

/** A thin bar whose filled width animates. Drawn, not laid out, so a change never relayouts. */
@Composable
fun AnimatedBar(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    delayMillis: Int = 0,
) {
    val shown = rememberSweepFraction(fraction, delayMillis)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(track, cornerRadius = r)
                val w = size.width * shown.value.coerceIn(0f, 1f)
                if (w > 0f) drawRoundRect(color, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
            },
    )
}


/**
 * Today's ring. It leads with the two numbers that matter: calories left (in the middle of the ring)
 * and protein left (the bar underneath). Carbs, fat and fibre sit in a collapsed "More detail".
 *
 * Motion: the three arcs sweep in on first show (protein first) and glide when values change, the
 * centre number counts up, [pulseTrigger] makes the ring pulse after a log and [burstTrigger] plays the
 * once-a-day protein burst. With reduce-motion everything is simply in its final state.
 *
 * The ring is one accessibility node describing the whole day; the animated number inside it never
 * speaks its in-between values.
 */
@Composable
fun MacroRing(
    progress: DayProgress,
    modifier: Modifier = Modifier,
    pulseTrigger: Int = 0,
    burstTrigger: Int = 0,
    proteinColor: Color = ProteinAccent,
    carbColor: Color = CarbAccent,
    fatColor: Color = FatAccent,
) {
    val target = progress.target
    val proteinFraction = rememberSweepFraction(progress.proteinFraction, 0)
    val carbFraction = rememberSweepFraction(progress.carbsFraction, 90)
    val fatFraction = rememberSweepFraction(progress.fatFraction, 180)
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(192.dp)
                .pulseOn(pulseTrigger)
                .clearAndSetSemantics { contentDescription = describe(progress) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(192.dp)) {
                // Protein outermost and thickest: the dominance is deliberate.
                val rings = listOf(
                    Triple(proteinFraction, proteinColor, 12.dp.toPx()),
                    Triple(carbFraction, carbColor, 9.dp.toPx()),
                    Triple(fatFraction, fatColor, 9.dp.toPx()),
                )
                var inset = 0f
                rings.forEach { (fractionState, color, stroke) ->
                    val fraction = fractionState.value
                    val half = stroke / 2f
                    val topLeft = Offset(inset + half, inset + half)
                    val arcSize = Size(
                        width = size.width - (inset + half) * 2,
                        height = size.height - (inset + half) * 2,
                    )
                    drawArc(
                        color = track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (fraction > 0f) {
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    inset += stroke + 7.dp.toPx()
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val remaining = progress.kcalRemaining
                if (target != null && remaining != null) {
                    CountUpText(
                        value = abs(remaining).toDouble(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (remaining >= 0) "kcal left" else "kcal over",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "%,d of %,d kcal eaten".format(progress.consumed.kcal.roundToInt(), target.kcal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CountUpText(
                        value = progress.consumed.kcal,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "kcal logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BurstOverlay(trigger = burstTrigger, color = proteinColor)
        }

        Spacer(Modifier.height(12.dp))

        // The second number that leads: protein.
        ProteinLead(progress, proteinColor, track)

        Spacer(Modifier.height(4.dp))
        MoreDetail(title = "More detail") {
            MacroBarRow("Carbs", progress.consumed.carbs, target?.carbsG, progress.carbsFraction, carbColor, track)
            Spacer(Modifier.height(8.dp))
            MacroBarRow("Fat", progress.consumed.fat, target?.fatG, progress.fatFraction, fatColor, track)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Fibre", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    "${progress.consumed.fiber.roundToInt()} g",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProteinLead(progress: DayProgress, color: Color, track: Color) {
    val target = progress.target
    val left = progress.proteinRemaining
    val consumed = progress.consumed.protein.roundToInt()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Protein", style = MaterialTheme.typography.titleSmall)
            InfoTip(text = Glossary.MACROS, label = "Protein, carbs and fat")
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    left == null -> "$consumed g"
                    left > 0 -> "$left g left"
                    else -> "Target met"
                },
                style = MaterialTheme.typography.titleSmall,
                color = color,
            )
        }
        AnimatedBar(progress.proteinFraction, color, track, height = 10.dp)
        Text(
            text = if (target != null) "$consumed of ${target.proteinG} g" else "$consumed g so far",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun MacroBarRow(label: String, consumed: Double, targetG: Int?, fraction: Float, color: Color, track: Color) {
    val text = if (targetG != null) "${consumed.roundToInt()} of $targetG g" else "${consumed.roundToInt()} g"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label $text" }) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(3.dp))
        AnimatedBar(fraction, color, track, height = 6.dp)
    }
}

private fun describe(progress: DayProgress): String {
    val target = progress.target
    return buildString {
        append("${progress.consumed.kcal.roundToInt()} calories")
        if (target != null) append(" of ${target.kcal}")
        append(". Protein ${progress.consumed.protein.roundToInt()} grams")
        if (target != null) append(" of ${target.proteinG}")
        progress.proteinRemaining?.let { if (it > 0) append(", $it grams left") }
        append(". Carbs ${progress.consumed.carbs.roundToInt()} grams")
        if (target != null) append(" of ${target.carbsG}")
        append(". Fat ${progress.consumed.fat.roundToInt()} grams")
        if (target != null) append(" of ${target.fatG}")
        append(".")
    }
}
