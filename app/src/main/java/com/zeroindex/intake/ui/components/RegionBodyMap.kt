package com.zeroindex.intake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zeroindex.intake.domain.training.RegionVolume
import com.zeroindex.intake.domain.training.VolumeStatus
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.training.muscles.BodyView
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import com.zeroindex.intake.domain.training.muscles.MuscleRegion

private const val BODY_GAP = 16.0
private const val BODY_TOTAL_WIDTH = MuscleMap.FIGURE_WIDTH * 2 + BODY_GAP

private fun outlinePath(points: List<MuscleMap.Pt>): Path = Path().apply {
    val segments = MuscleMap.smooth(points)
    if (segments.isEmpty()) return@apply
    moveTo(segments[0].x0.toFloat(), segments[0].y0.toFloat())
    segments.forEach { c -> cubicTo(c.x1.toFloat(), c.y1.toFloat(), c.x2.toFloat(), c.y2.toFloat(), c.x3.toFloat(), c.y3.toFloat()) }
    close()
}

private class BodyPaths {
    val silhouette: List<Path> = MuscleMap.silhouette().map(::outlinePath)
    val front: List<Pair<MuscleRegion, Path>> = MuscleMap.outlines(BodyView.FRONT).map { it.region to outlinePath(it.points) }
    val back: List<Pair<MuscleRegion, Path>> = MuscleMap.outlines(BodyView.BACK).map { it.region to outlinePath(it.points) }
}

/** The colour a region is drawn in for a status; the words ("under", "in range", "over") always travel with it. */
@Composable
fun volumeColor(status: VolumeStatus, hasSets: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        !hasSets -> scheme.onSurface.copy(alpha = 0.10f)
        status == VolumeStatus.UNDER -> scheme.secondary.copy(alpha = 0.85f)
        status == VolumeStatus.IN_RANGE -> scheme.primary
        else -> scheme.tertiary
    }
}

/**
 * The 6-region body map of "This week" on Train: front and back, each region shaded by how this week's sets compare with the
 * target (under, in range, over). Tapping a region reports it; the screen also lists every region as text, so nothing is
 * carried by colour alone.
 */
@Composable
fun RegionBodyMap(
    volumes: List<RegionVolume>,
    selected: BodyRegion?,
    onRegionTap: (BodyRegion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val paths = remember { BodyPaths() }
    val byRegion = volumes.associateBy { it.region }
    val colors = BodyRegion.entries.associateWith { r -> byRegion[r]?.let { volumeColor(it.status, it.sets > 0.0) } ?: scheme.onSurface.copy(alpha = 0.10f) }
    val body = scheme.surfaceVariant
    val edge = scheme.outline.copy(alpha = 0.55f)
    val pick = scheme.onSurface
    val description = "Body map of this week's sets. " + volumes.joinToString(" ") {
        "${it.region.label}: ${it.summary()}, ${com.zeroindex.intake.domain.training.WeeklyVolume.words(it.status)}."
    }

    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((BODY_TOTAL_WIDTH / MuscleMap.FIGURE_HEIGHT).toFloat())
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val s = size.width / BODY_TOTAL_WIDTH
                        val ux = tap.x / s
                        val uy = tap.y / s
                        val region = when {
                            ux <= MuscleMap.FIGURE_WIDTH -> MuscleMap.regionAt(BodyView.FRONT, ux, uy)
                            ux >= MuscleMap.FIGURE_WIDTH + BODY_GAP -> MuscleMap.regionAt(BodyView.BACK, ux - MuscleMap.FIGURE_WIDTH - BODY_GAP, uy)
                            else -> null
                        }
                        region?.let { onRegionTap(it.body) }
                    }
                },
        ) {
            val s = (size.width / BODY_TOTAL_WIDTH).toFloat()
            fun drawView(xOffset: Double, regions: List<Pair<MuscleRegion, Path>>) {
                withTransform({
                    translate(left = (xOffset * s).toFloat(), top = 0f)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    paths.silhouette.forEach { drawPath(it, color = body) }
                    regions.forEach { (region, path) ->
                        drawPath(path, color = colors.getValue(region.body))
                        val isSelected = region.body == selected
                        drawPath(path, color = if (isSelected) pick else edge, style = Stroke(width = if (isSelected) 1.1f else 0.45f))
                    }
                }
            }
            drawView(0.0, paths.front)
            drawView(MuscleMap.FIGURE_WIDTH + BODY_GAP, paths.back)
        }
    }
}
