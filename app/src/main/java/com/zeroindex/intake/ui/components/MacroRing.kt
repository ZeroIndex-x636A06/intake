package com.zeroindex.intake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.domain.nutrition.DayProgress
import kotlin.math.roundToInt

/**
 * SPEC §5: calories against target in the centre, three arcs for protein, carbs
 * and fat, with protein visually dominant because it is the number that
 * matters. Protein gets the outermost and thickest arc.
 *
 * Every arc is also labelled in text underneath, so the ring is never the only
 * carrier of the information (SPEC §3.7), and the whole ring is announced as
 * one sentence rather than as a pile of unlabelled numbers.
 */
@Composable
fun MacroRing(
    progress: DayProgress,
    proteinColor: Color,
    carbColor: Color,
    fatColor: Color,
    modifier: Modifier = Modifier,
) {
    val target = progress.target
    val proteinFraction by animateFloatAsState(progress.proteinFraction, tween(450), label = "protein")
    val carbFraction by animateFloatAsState(progress.carbsFraction, tween(450), label = "carbs")
    val fatFraction by animateFloatAsState(progress.fatFraction, tween(450), label = "fat")

    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val description = buildString {
        append("${progress.consumed.kcal.roundToInt()} calories")
        if (target != null) append(" of ${target.kcal}")
        append(". Protein ${progress.consumed.protein.roundToInt()} grams")
        if (target != null) append(" of ${target.proteinG}")
        append(". Carbs ${progress.consumed.carbs.roundToInt()} grams")
        if (target != null) append(" of ${target.carbsG}")
        append(". Fat ${progress.consumed.fat.roundToInt()} grams")
        if (target != null) append(" of ${target.fatG}")
        append(".")
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.60f)
                .aspectRatio(1f)
                .clearAndSetSemantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                // Protein outermost and thickest: the dominance is deliberate.
                val rings = listOf(
                    Triple(proteinFraction, proteinColor, 20f),
                    Triple(carbFraction, carbColor, 13f),
                    Triple(fatFraction, fatColor, 13f),
                )
                var inset = 0f
                rings.forEach { (fraction, color, widthDp) ->
                    val stroke = widthDp.dp.toPx()
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
                    inset += stroke + 6.dp.toPx()
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${progress.consumed.kcal.roundToInt()}",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                )
                Text(
                    text = if (target != null) "of ${target.kcal} kcal" else "kcal logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MacroLegendItem("Protein", progress.consumed.protein, target?.proteinG, proteinColor)
            MacroLegendItem("Carbs", progress.consumed.carbs, target?.carbsG, carbColor)
            MacroLegendItem("Fat", progress.consumed.fat, target?.fatG, fatColor)
        }
    }
}

@Composable
private fun MacroLegendItem(label: String, consumed: Double, target: Int?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Paired with the text label, so colour is never the only signal.
            Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (target != null) {
                "${consumed.roundToInt()} / $target g"
            } else {
                "${consumed.roundToInt()} g"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
