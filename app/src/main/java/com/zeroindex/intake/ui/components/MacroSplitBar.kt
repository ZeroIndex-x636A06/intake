package com.zeroindex.intake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.nutrition.MacroLabelFit
import com.zeroindex.intake.domain.nutrition.MacroShares
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.ProteinAccent

/** Room each label keeps in its segment for the padding before it. */
private val LabelLeadDp = 2f

/**
 * One rounded bar split into protein, carbs and fat by their share of calories, with each percentage written under its
 * segment in that segment's colour. A label is drawn only where its segment is wide enough for it ([MacroLabelFit]), so
 * labels never overlap or clip; the description still reads every percentage. Shown alongside the numbers, never instead
 * of them. Draws nothing when there are no calories to split.
 */
@Composable
fun MacroSplitBar(
    proteinGrams: Double,
    carbGrams: Double,
    fatGrams: Double,
    modifier: Modifier = Modifier,
    /** False draws the bar alone (a compact row); the description still reads the percentages. */
    showLabels: Boolean = true,
    barHeight: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val shares = MacroShares.of(proteinGrams, carbGrams, fatGrams) ?: return
    val segments = listOf(
        Triple(shares.proteinPct, ProteinAccent, "protein"),
        Triple(shares.carbPct, CarbAccent, "carbs"),
        Triple(shares.fatPct, FatAccent, "fat"),
    ).filter { it.first > 0 }
    val description = "Share of calories: protein ${shares.proteinPct}%, carbs ${shares.carbPct}%, fat ${shares.fatPct}%"

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall
    // The widest label a segment can carry ("100%"), so one measure decides every label and nothing can overflow.
    val labelWidthDp = with(density) { measurer.measure("100%", labelStyle).size.width.toDp().value } + LabelLeadDp

    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(barHeight / 2))) {
            segments.forEach { (pct, color, _) ->
                Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(color))
            }
        }
        if (showLabels) {
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val shown = MacroLabelFit.visible(segments.map { it.first }, maxWidth.value, labelWidthDp)
                Row(Modifier.fillMaxWidth()) {
                    segments.forEachIndexed { i, (pct, color, _) ->
                        Box(Modifier.weight(pct.toFloat()).padding(start = LabelLeadDp.dp)) {
                            if (shown[i]) {
                                Text(
                                    text = "$pct%",
                                    style = labelStyle,
                                    color = color,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
