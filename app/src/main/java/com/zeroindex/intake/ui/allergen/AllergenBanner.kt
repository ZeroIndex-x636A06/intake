package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.allergen.AllergenEvidence
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.theme.AllergenAmber
import com.zeroindex.intake.ui.theme.AllergenAmberBg
import com.zeroindex.intake.ui.theme.AllergenRed
import com.zeroindex.intake.ui.theme.AllergenRedBg
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * One colour language for allergen status, used by Log food, Food detail, Photo estimate and Swap: red text on pale red for
 * "contains" and blocked, amber on pale amber for "may contain" and "check the label", green with a check only for a result
 * that read a label or panel. A model or an imported file can raise a status here but never lower it. The pairs are the
 * theme's `bad` / `warn` / `ok` tokens, so they follow light and dark.
 */
object AllergenColors {
    val red: Color @Composable @ReadOnlyComposable get() = AllergenRed
    val amber: Color @Composable @ReadOnlyComposable get() = AllergenAmber
    val redBg: Color @Composable @ReadOnlyComposable get() = AllergenRedBg
    val amberBg: Color @Composable @ReadOnlyComposable get() = AllergenAmberBg

    /** Text on a solid red or amber fill (the theme's `onError`). */
    val onSolid: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onError
}

/** The verdict a scan gives, in the swap sheet's terms (worst wins). */
fun AllergenScanResult.verdict(): SwapAllergen = com.zeroindex.intake.domain.swap.SwapMath.verdict(
    contains = contains.isNotEmpty(),
    mayContain = mayContain.isNotEmpty(),
    checkLabel = checkLabel.isNotEmpty(),
)

/** The words a row chip adds after "Contains" / "May contain": the worst tier's matched terms, or null when clear. */
fun AllergenScanResult.chipDetail(): String? = when {
    contains.isNotEmpty() -> contains.termsText()
    mayContain.isNotEmpty() -> com.zeroindex.intake.domain.allergen.ChipWords.allergenNames(mayContain)
    checkLabel.isNotEmpty() -> checkLabel.termsText()
    else -> null
}

/**
 * [chipDetail] for a saved recipe or template (Q6-8): the matched ingredient words, never the recipe's own title
 * listed as if it were an ingredient. The verdict and tier are untouched.
 */
fun AllergenScanResult.recipeChipDetail(): String? = when {
    contains.isNotEmpty() -> com.zeroindex.intake.domain.allergen.ChipWords.ingredientTerms(contains)
    mayContain.isNotEmpty() -> com.zeroindex.intake.domain.allergen.ChipWords.allergenNames(mayContain)
    checkLabel.isNotEmpty() -> com.zeroindex.intake.domain.allergen.ChipWords.ingredientTerms(checkLabel)
    else -> null
}

/** The chip words for a hit that is only a label's trace line: "May contain milk (trace)". Null for anything else. */
fun AllergenScanResult.traceChipLabel(): String? =
    if (containsOnlyTraces) "May contain ${contains.termsText()} (trace)" else null

/** "milk and peanuts", from the matched allergens of a tier. */
fun List<com.zeroindex.intake.domain.allergen.AllergenMatch>.termsText(): String =
    map { it.matchedTerm }.distinct().let { t ->
        when (t.size) {
            0 -> ""
            1 -> t[0]
            else -> t.dropLast(1).joinToString(", ") + " and " + t.last()
        }
    }

/** The full-width block at the top of Food detail: red for contains, amber for may contain or check the label. */
@Composable
fun AllergenBanner(scan: AllergenScanResult, modifier: Modifier = Modifier) {
    when {
        scan.contains.isNotEmpty() -> StatusBanner(
            bg = AllergenColors.redBg,
            fg = AllergenColors.red,
            title = if (scan.containsOnlyTraces) "May contain traces of ${scan.contains.termsText()}" else "Contains ${scan.contains.termsText()}",
            body = if (scan.containsOnlyTraces) {
                "The label lists it as a possible trace, not an ingredient. On your list: logging needs you to confirm."
            } else {
                "On your list. Logging needs you to confirm. Found by the local check, so nothing else can clear it."
            },
            modifier = modifier,
        )
        scan.mayContain.isNotEmpty() -> StatusBanner(
            bg = AllergenColors.amberBg,
            fg = AllergenColors.amber,
            title = "May contain ${com.zeroindex.intake.domain.allergen.ChipWords.allergenNames(scan.mayContain)}",
            body = scan.mayContain.first().note,
            modifier = modifier,
        )
        scan.checkLabel.isNotEmpty() -> StatusBanner(
            bg = AllergenColors.amberBg,
            fg = AllergenColors.amber,
            title = "Check the label",
            body = scan.checkLabel.first().note,
            modifier = modifier,
            icon = null,
        )
        else -> Unit
    }
}

@Composable
private fun StatusBanner(bg: Color, fg: Color, title: String, body: String, icon: ImageVector? = IntakeIcons.ShieldAlert, modifier: Modifier = Modifier) {
    Surface(color = bg, contentColor = fg, shape = IntakeShape.Card, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.Large))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A small status chip for one row (a search result, a drink, a swap option). Clear shows the green "Fits your allergens" with a
 * check (only for a result that read a label; see [AllergenEvidence.ClearBasis]); Check the label and May contain are amber,
 * Contains is red. The words never change, and Contains is never softer than May contain.
 */
@Composable
fun AllergenChip(
    verdict: SwapAllergen,
    detail: String? = null,
    modifier: Modifier = Modifier,
    showClear: Boolean = true,
    /** Replaces the chip's words, for a reason already written out ("May contain milk: ask"). */
    label: String? = null,
) {
    val tone: ChipTone
    val icon: ImageVector?
    val defaultText: String
    when (verdict) {
        SwapAllergen.CLEAR -> {
            if (!showClear) return
            tone = ChipTone.Ok
            icon = IntakeIcons.Check
            defaultText = AllergenEvidence.ClearBasis.LABEL_READ.chipText
        }
        SwapAllergen.CHECK_LABEL -> {
            tone = ChipTone.Warn
            icon = null
            defaultText = detail?.let { "Check the label: $it" } ?: "Check the label"
        }
        SwapAllergen.MAY_CONTAIN -> {
            tone = ChipTone.Warn
            icon = null
            defaultText = detail?.let { "May contain $it" } ?: "May contain"
        }
        SwapAllergen.CONTAINS -> {
            tone = ChipTone.Bad
            icon = null
            defaultText = detail?.let { "Contains $it" } ?: "Contains your allergen"
        }
    }
    val text = label ?: defaultText
    IntakeChip(
        text = text,
        tone = tone,
        icon = icon,
        maxLines = Int.MAX_VALUE,
        modifier = modifier.semantics { contentDescription = text },
    )
}
