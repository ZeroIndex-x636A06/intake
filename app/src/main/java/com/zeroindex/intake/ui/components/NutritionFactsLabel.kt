package com.zeroindex.intake.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.domain.nutrition.LabelFormat
import com.zeroindex.intake.domain.nutrition.LabelNutrient
import com.zeroindex.intake.domain.nutrition.LabelRow
import com.zeroindex.intake.domain.nutrition.NutritionLabel

/**
 * The familiar FDA-style Nutrition Facts panel: a bold title, the serving line, a heavy rule, Calories large, % Daily
 * Value, and one ruled row per nutrient. It is a view of [NutritionLabel] (stored per-100 values times the portion), never
 * an image. A nutrient the source did not give reads "not listed" in place of an amount, and has no % Daily Value.
 */
@Composable
fun NutritionFactsLabel(label: NutritionLabel, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, ink),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Nutrition Facts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = ink)
            Text(label.mode.label + " · " + label.servingText, style = MaterialTheme.typography.bodyMedium, color = soft)
            Rule(1.dp, ink)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Serving size", fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.weight(1f))
                Text(label.servingText, fontWeight = FontWeight.Bold, color = ink)
            }
            Rule(8.dp, ink)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text("Calories", fontWeight = FontWeight.ExtraBold, color = ink, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
                Text(
                    LabelFormat.calories(label.calories).toString(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ink,
                )
            }
            Rule(5.dp, ink)
            Text(
                "% Daily Value*",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            label.rows.forEach { row ->
                Rule(1.dp, ink.copy(alpha = 0.7f))
                LabelLine(row)
            }
            Rule(8.dp, ink)
            Text(NutritionLabel.MICRONUTRIENTS, style = MaterialTheme.typography.bodySmall, color = soft, modifier = Modifier.padding(top = 4.dp))
            Text(NutritionLabel.FOOTNOTE, style = MaterialTheme.typography.labelSmall, color = soft, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun LabelLine(row: LabelRow) {
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(start = (row.indent * 14).dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val trans = row.nutrient == LabelNutrient.TRANS_FAT
        val title = if (row.nutrient == LabelNutrient.ADDED_SUGARS) "Added Sugars" else row.name
        Text(
            buildAnnotatedLine(title, row),
            modifier = Modifier.weight(1f),
            fontStyle = if (trans) FontStyle.Italic else FontStyle.Normal,
            color = ink,
        )
        Text(
            row.percentDv?.let { "$it%" } ?: "",
            fontWeight = FontWeight.Bold,
            color = if (row.listed) ink else soft,
        )
    }
}

/** "Total Fat 9 g" with the name bold for the main rows, or "Sodium not listed". */
@Composable
private fun buildAnnotatedLine(title: String, row: LabelRow) = buildAnnotatedString {
    val amount = LabelFormat.amount(row)
    withStyle(SpanStyle(fontWeight = if (row.bold) FontWeight.Bold else FontWeight.Normal)) { append(title) }
    append(" ")
    if (amount != null) append(amount)
    else withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)) {
        append(NutritionLabel.NOT_LISTED)
    }
}

@Composable
private fun Rule(thickness: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxWidth().height(thickness).background(color))
}
