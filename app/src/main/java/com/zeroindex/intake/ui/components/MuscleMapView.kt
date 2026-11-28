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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import com.zeroindex.intake.domain.training.muscles.MuscleHighlight
import com.zeroindex.intake.domain.training.muscles.MuscleRegion

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
    val paths = remember { FigurePaths() }
    val primary = scheme.primary
    val secondary = scheme.primary.copy(alpha = 0.38f)
    val idle = scheme.onSurface.copy(alpha = 0.07f)
    val body = scheme.surfaceVariant
    val figureColors = FigureColors(body = body, bodyEdge = scheme.outline.copy(alpha = 0.5f), edge = scheme.outline.copy(alpha = 0.65f), line = scheme.outline.copy(alpha = 0.5f), selected = scheme.onSurface)

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
                .aspectRatio((FIGURE_TOTAL_WIDTH / MuscleMap.FIGURE_HEIGHT).toFloat())
                .then(
                    if (onRegionTap == null) Modifier else Modifier.pointerInput(Unit) {
                        detectTapGestures { tap -> figureRegionAt(size.width.toFloat(), tap.x, tap.y)?.let(onRegionTap) }
                    },
                ),
        ) {
            drawFigure(paths, figureColors, selected = null) { region ->
                if (detail) {
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
            }
        }
        if (showLabels) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("Front", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text("Back", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
