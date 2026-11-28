package com.zeroindex.intake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.nutrition.MacroShares
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.ProteinAccent

/**
 * One rounded bar split into protein, carbs and fat by their share of calories. Under it, one line names each share
 * with its letter in that segment's colour ("P 4%  C 93%  F 3%"). The letters are written out in a line rather than
 * placed under each segment, so a 4% segment never clips its label (QA Q1-9, Q1-20). Draws nothing when there are no
 * calories to split.
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
        Triple(shares.proteinPct, ProteinAccent, "P"),
        Triple(shares.carbPct, CarbAccent, "C"),
        Triple(shares.fatPct, FatAccent, "F"),
    ).filter { it.first > 0 }
    val description = "Share of calories: protein ${shares.proteinPct}%, carbs ${shares.carbPct}%, fat ${shares.fatPct}%"

    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(barHeight / 2))) {
            segments.forEach { (pct, color, _) ->
                Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(color))
            }
        }
        if (showLabels) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildAnnotatedString {
                    segments.forEachIndexed { i, (pct, color, letter) ->
                        if (i > 0) append("  ")
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append("$letter $pct%") }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
