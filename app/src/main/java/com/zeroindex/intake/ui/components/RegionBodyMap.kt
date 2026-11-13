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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zeroindex.intake.domain.training.MuscleVolume
import com.zeroindex.intake.domain.training.VolumeStatus
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import com.zeroindex.intake.domain.training.muscles.MuscleRegion

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
 * The anatomical body map of "This week" on Train: front and back figure, every muscle region shaded by how this week's
 * weighted sets compare with its target (under, in range, over). Tapping a region reports it; the screen lists the
 * exercises that counted for it and every body region as text, so nothing is carried by colour alone.
 */
@Composable
fun RegionBodyMap(
    muscles: List<MuscleVolume>,
    selected: MuscleRegion?,
    onRegionTap: (MuscleRegion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val paths = remember { FigurePaths() }
    val byRegion = muscles.associateBy { it.region }
    val idle = scheme.onSurface.copy(alpha = 0.10f)
    val colors = MuscleRegion.entries.associateWith { r -> byRegion[r]?.let { volumeColor(it.status, it.sets > 0.0) } ?: idle }
    val figureColors = FigureColors(
        body = scheme.surfaceVariant,
        bodyEdge = scheme.outline.copy(alpha = 0.5f),
        edge = scheme.outline.copy(alpha = 0.65f),
        line = scheme.outline.copy(alpha = 0.5f),
        selected = scheme.onSurface,
    )
    val description = "Body map of this week's sets. " + muscles.filter { it.sets > 0.0 }.joinToString(" ") {
        "${it.region.label}: ${it.summary()}, ${WeeklyVolume.words(it.status)}."
    }.ifEmpty { "Nothing logged yet." }

    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((FIGURE_TOTAL_WIDTH / MuscleMap.FIGURE_HEIGHT).toFloat())
                .pointerInput(Unit) {
                    detectTapGestures { tap -> figureRegionAt(size.width.toFloat(), tap.x, tap.y)?.let(onRegionTap) }
                },
        ) {
            drawFigure(paths, figureColors, selected) { colors.getValue(it) }
        }
    }
}
