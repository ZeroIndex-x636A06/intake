package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.ui.theme.AllergenAmber
import com.zeroindex.intake.ui.theme.AllergenRed
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * One colour language for allergen status, used by Log food, Food detail, Photo estimate and Swap (round 3, R3-3):
 * solid red with white text for "contains", solid amber for "may contain", a quieter sand for "check the label", and a
 * calm green for clear. A model or an imported file can raise a status here but never lower it.
 */
object AllergenColors {
    val red: Color = AllergenRed
    val amber: Color = AllergenAmber
    val onSolid: Color = Color.White
}

/** The verdict a scan gives, in the swap sheet's terms (worst wins). */
fun AllergenScanResult.verdict(): SwapAllergen = com.zeroindex.intake.domain.swap.SwapMath.verdict(
    contains = contains.isNotEmpty(),
    mayContain = mayContain.isNotEmpty(),
    checkLabel = checkLabel.isNotEmpty(),
)

/** "milk and peanuts", from the matched allergens of a tier. */
fun List<com.zeroindex.intake.domain.allergen.AllergenMatch>.termsText(): String =
    map { it.matchedTerm }.distinct().let { t ->
        when (t.size) {
            0 -> ""
            1 -> t[0]
            else -> t.dropLast(1).joinToString(", ") + " and " + t.last()
        }
    }

/** The full-width block at the top of Food detail: red, amber or sand, or a small "no allergens" line. */
@Composable
fun AllergenBanner(scan: AllergenScanResult, modifier: Modifier = Modifier) {
    when {
        scan.contains.isNotEmpty() -> SolidBanner(
            color = AllergenColors.red,
            icon = Icons.Filled.Warning,
            title = "Contains ${scan.contains.termsText()}",
            body = "On your list. Logging needs you to confirm. Found by the local check, so nothing else can clear it.",
            modifier = modifier,
        )
        scan.mayContain.isNotEmpty() -> SolidBanner(
            color = AllergenColors.amber,
            icon = Icons.Filled.Warning,
            title = "May contain ${scan.mayContain.termsText()}",
            body = scan.mayContain.first().note,
            modifier = modifier,
        )
        scan.checkLabel.isNotEmpty() -> Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = IntakeShape.Banner,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Check the label", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                Text(scan.checkLabel.first().note, style = MaterialTheme.typography.bodySmall)
            }
        }
        else -> Unit
    }
}

@Composable
private fun SolidBanner(color: Color, icon: ImageVector, title: String, body: String, modifier: Modifier) {
    Surface(color = color, contentColor = AllergenColors.onSolid, shape = IntakeShape.Banner, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * A small status chip for one row (a search result, a drink, a swap option). Clear shows a quiet green "No allergens";
 * the others are solid.
 */
@Composable
fun AllergenChip(verdict: SwapAllergen, detail: String? = null, modifier: Modifier = Modifier, showClear: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, icon, text) = when (verdict) {
        SwapAllergen.CLEAR -> {
            if (!showClear) return
            Quad(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Filled.Check, "No allergens")
        }
        SwapAllergen.CHECK_LABEL -> Quad(scheme.secondaryContainer, scheme.onSecondaryContainer, null, "Check the label")
        SwapAllergen.MAY_CONTAIN -> Quad(AllergenColors.amber, AllergenColors.onSolid, Icons.Filled.Warning, detail?.let { "May contain $it" } ?: "May contain")
        SwapAllergen.CONTAINS -> Quad(AllergenColors.red, AllergenColors.onSolid, Icons.Filled.Close, detail?.let { "Contains $it" } ?: "Contains your allergen")
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = CircleShape,
        border = if (verdict == SwapAllergen.CHECK_LABEL) BorderStroke(1.dp, scheme.outline) else null,
        modifier = modifier.semantics { contentDescription = text },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

private data class Quad(val bg: Color, val fg: Color, val icon: ImageVector?, val text: String)
