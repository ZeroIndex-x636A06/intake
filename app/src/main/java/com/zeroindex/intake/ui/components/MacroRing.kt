package com.zeroindex.intake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.nutrition.DayProgress
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.ProteinAccent
import kotlin.math.roundToInt

/**
 * SPEC §5: calories against target in the centre, three arcs for protein, carbs
 * and fat, with protein visually dominant because it is the number that
 * matters - it gets the outermost ring and a thicker stroke.
 *
 * Laid out as in design/intake-mockup.html: a 192dp ring, the running total and
 * remaining calories stacked in the middle, and three tiles underneath. The
 * protein tile also spells out grams remaining, which is the figure SPEC §5
 * says to put in front of the user.
 *
 * The ring is one accessibility node describing the whole day, since a stack of
 * unlabelled arcs tells a screen reader nothing.
 */
@Composable
fun MacroRing(
    progress: DayProgress,
    modifier: Modifier = Modifier,
    proteinColor: Color = ProteinAccent,
    carbColor: Color = CarbAccent,
    fatColor: Color = FatAccent,
) {
    val target = progress.target
    val proteinFraction by animateFloatAsState(progress.proteinFraction, tween(450), label = "protein")
    val carbFraction by animateFloatAsState(progress.carbsFraction, tween(450), label = "carbs")
    val fatFraction by animateFloatAsState(progress.fatFraction, tween(450), label = "fat")

    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(192.dp)
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
                rings.forEach { (fraction, color, stroke) ->
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
                Text(
                    text = "%,d".format(progress.consumed.kcal.roundToInt()),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (target != null) {
                        "of %,d kcal".format(target.kcal)
                    } else {
                        "kcal logged"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                progress.kcalRemaining?.let { remaining ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (remaining >= 0) {
                            "%,d kcal remaining".format(remaining)
                        } else {
                            "%,d kcal over".format(-remaining)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remaining >= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatTile(
                value = amount(progress.consumed.protein, target?.proteinG),
                // The one macro whose remainder is spelled out, per SPEC §5.
                caption = progress.proteinRemaining
                    ?.let { if (it > 0) "protein · left ${it} g" else "protein · met" }
                    ?: "protein",
                valueColor = proteinColor,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = amount(progress.consumed.carbs, target?.carbsG),
                caption = "carbs",
                valueColor = carbColor,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = amount(progress.consumed.fat, target?.fatG),
                caption = "fat",
                valueColor = fatColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun amount(consumed: Double, target: Int?): String =
    if (target != null) "${consumed.roundToInt()} / $target g" else "${consumed.roundToInt()} g"

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
