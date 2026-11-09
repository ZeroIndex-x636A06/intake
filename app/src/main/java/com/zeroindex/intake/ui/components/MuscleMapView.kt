package com.zeroindex.intake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.zeroindex.intake.domain.training.muscles.BodyView
import com.zeroindex.intake.domain.training.muscles.MuscleHighlight
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import com.zeroindex.intake.domain.training.muscles.MuscleRegion

private const val GAP = 16.0
private const val TOTAL_WIDTH = MuscleMap.FIGURE_WIDTH * 2 + GAP

private fun pathOf(points: List<MuscleMap.Pt>): Path = Path().apply {
    val segments = MuscleMap.smooth(points)
    if (segments.isEmpty()) return@apply
    moveTo(segments[0].x0.toFloat(), segments[0].y0.toFloat())
    segments.forEach { c ->
        cubicTo(c.x1.toFloat(), c.y1.toFloat(), c.x2.toFloat(), c.y2.toFloat(), c.x3.toFloat(), c.y3.toFloat())
    }
    close()
}

private class MapPaths {
    val silhouette: List<Path> = MuscleMap.silhouette().map(::pathOf)
    val front = MuscleMap.outlines(BodyView.FRONT).map { it.region to pathOf(it.points) }
    val back = MuscleMap.outlines(BodyView.BACK).map { it.region to pathOf(it.points) }
}

/**
 * A front and back body silhouette with the worked muscles lit: primary strongly, secondary lightly.
 * Colours come from the theme, so it reads in light and dark. With [onRegionTap] set, tapping a part
 * of the body reports the region (the custom-exercise and profile pickers use it, and always offer
 * a list as well).
 */
@Composable
fun MuscleMapView(
    highlight: MuscleHighlight,
    modifier: Modifier = Modifier,
    onRegionTap: ((MuscleRegion) -> Unit)? = null,
    showLabels: Boolean = true,
    /** False lights whole body regions (chest, back, shoulders, arms, legs, core); true lights the fine groups. */
    detail: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val paths = remember { MapPaths() }
    val primary = scheme.primary
    val secondary = scheme.primary.copy(alpha = 0.38f)
    val idle = scheme.onSurface.copy(alpha = 0.07f)
    val body = scheme.surfaceVariant
    val edge = scheme.outline.copy(alpha = 0.55f)

    val coarsePrimary = highlight.primary.map { it.body }.toSet()
    val coarseSecondary = highlight.secondary.map { it.body }.toSet() - coarsePrimary

    val description = buildString {
        append("Muscle map. ")
        if (highlight.isEmpty) {
            append("No muscles highlighted.")
        } else {
            if (highlight.primary.isNotEmpty()) append("Main: ").append(highlight.primary.joinToString { it.label }).append(". ")
            if (highlight.secondary.isNotEmpty()) append("Also: ").append(highlight.secondary.joinToString { it.label }).append(".")
        }
    }

    androidx.compose.foundation.layout.Column(modifier = modifier.semantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((TOTAL_WIDTH / MuscleMap.FIGURE_HEIGHT).toFloat())
                .then(
                    if (onRegionTap == null) Modifier else Modifier.pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val s = size.width / TOTAL_WIDTH
                            val ux = tap.x / s
                            val uy = tap.y / s
                            val region = when {
                                ux <= MuscleMap.FIGURE_WIDTH -> MuscleMap.regionAt(BodyView.FRONT, ux, uy)
                                ux >= MuscleMap.FIGURE_WIDTH + GAP -> MuscleMap.regionAt(BodyView.BACK, ux - MuscleMap.FIGURE_WIDTH - GAP, uy)
                                else -> null
                            }
                            if (region != null) onRegionTap(region)
                        }
                    },
                ),
        ) {
            val s = (size.width / TOTAL_WIDTH).toFloat()
            fun drawView(xOffset: Double, regions: List<Pair<MuscleRegion, Path>>) {
                withTransform({
                    translate(left = (xOffset * s).toFloat(), top = 0f)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    paths.silhouette.forEach { drawPath(it, color = body) }
                    regions.forEach { (region, path) ->
                        val fill = if (detail) {
                            when (region) {
                                in highlight.primary -> primary
                                in highlight.secondary -> secondary
                                else -> idle
                            }
                        } else {
                            when (region.body) {
                                in coarsePrimary -> primary
                                in coarseSecondary -> secondary
                                else -> idle
                            }
                        }
                        drawPath(path, color = fill)
                        drawPath(path, color = edge, style = Stroke(width = 0.45f))
                    }
                }
            }
            drawView(0.0, paths.front)
            drawView(MuscleMap.FIGURE_WIDTH + GAP, paths.back)
        }
        if (showLabels) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("Front", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text("Back", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
