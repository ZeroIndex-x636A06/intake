package com.zeroindex.intake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.nutrition.MacroShares
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.ProteinAccent

/**
 * One rounded bar split into protein, carbs and fat by their share of calories, with each percentage written under its
 * segment in that segment's colour. Shown alongside the numbers, never instead of them. Draws nothing when there are no
 * calories to split.
 */
@Composable
fun MacroSplitBar(proteinGrams: Double, carbGrams: Double, fatGrams: Double, modifier: Modifier = Modifier) {
    val shares = MacroShares.of(proteinGrams, carbGrams, fatGrams) ?: return
    val segments = listOf(
        Triple(shares.proteinPct, ProteinAccent, "protein"),
        Triple(shares.carbPct, CarbAccent, "carbs"),
        Triple(shares.fatPct, FatAccent, "fat"),
    ).filter { it.first > 0 }
    val description = "Share of calories: protein ${shares.proteinPct}%, carbs ${shares.carbPct}%, fat ${shares.fatPct}%"

    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            segments.forEach { (pct, color, _) ->
                Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(color))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            segments.forEach { (pct, color, _) ->
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(pct.toFloat()).padding(start = 2.dp),
                )
            }
        }
    }
}
