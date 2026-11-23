@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.MatchWording
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.DangerButton
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.SoftCard
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.intake

/**
 * SPEC §7.2: a full-width confirmation sheet naming the exact matched term and where it was found, with an explicit
 * "log it anyway" tap. Never a toast, never a small banner.
 *
 * DESIGN-V6 food safety sheet: a red (Contains) or amber (May contain) shield and eyebrow, the headline, a soft card with
 * the food and its status chip, then the actions stacked: "Don't log it" is the primary ink button and "Log it anyway" is
 * the danger button. The safe choice stays the default. Order, labels and tiers are unchanged.
 *
 * SPEC §7.3 keeps the two severities visibly apart - a declared "contains" is red, a "may contain" amber - and each carries
 * words as well as colour, since colour is never the only signal (SPEC §3.7).
 */
/**
 * Opens the Swap sheet for a meal slot (null: the slot for the time of day). Provided once by the nav host. It is null where
 * there is no host, and then "Find a safer swap" is not shown. Opening it never logs anything.
 */
val LocalFindSwap = androidx.compose.runtime.staticCompositionLocalOf<((String?) -> Unit)?> { null }

/** The `onFindSwap` argument for an [AllergenWarningSheet] call site: null when nothing can open the Swap sheet. */
@Composable
fun rememberFindSwap(slot: String? = null): (() -> Unit)? {
    val open = LocalFindSwap.current ?: return null
    return { open(slot) }
}

@Composable
fun AllergenWarningSheet(
    foodName: String,
    scan: AllergenScanResult,
    onDismiss: () -> Unit,
    onLogAnyway: () -> Unit,
    /** Shown with the matched term underlined, as the mockup does. */
    ingredientText: String? = null,
    foodDetail: String? = null,
    /** SPEC §7.4: the model's own reasoning, shown verbatim for AI results. */
    aiReasoning: String? = null,
    /**
     * "Find a safer swap": shown only when the caller passes it. Tapping closes the sheet through [onDismiss] (nothing is
     * logged) and then calls this to open the Swap sheet.
     */
    onFindSwap: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hardBlock = scan.blocks
    val tone = if (hardBlock) MaterialTheme.intake.bad else MaterialTheme.intake.warn
    val toneBg = if (hardBlock) MaterialTheme.intake.badBg else MaterialTheme.intake.warnBg

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Sheet,
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = toneBg, contentColor = tone, shape = IntakeShape.Tile, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = IntakeIcons.ShieldAlert,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Default),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Eyebrow(
                        text = if (hardBlock) "Food safety check" else "Check this item",
                        color = tone,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = if (hardBlock && scan.containsOnlyTraces) {
                            "The label lists a possible trace"
                        } else if (hardBlock) {
                            "This contains an ingredient you avoid"
                        } else {
                            "Possible cross-contact"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // The food itself, with an unmistakable "not saved" state, and the reason beneath it.
            SoftCard(contentPadding = PaddingValues(16.dp)) {
                Eyebrow(
                    text = if (hardBlock) {
                        "Save blocked · ${scan.contains.first().allergen} detected"
                    } else {
                        "May contain · soft match"
                    },
                    color = tone,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        foodName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IntakeChip(
                        text = if (hardBlock) "not saved" else "review",
                        tone = if (hardBlock) ChipTone.Bad else ChipTone.Warn,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (hardBlock) {
                        // Says where it matched (the name, the ingredient list, the tags), not always "an ingredient".
                        MatchWording.blockedBody(scan.contains)
                    } else {
                        scan.mayContain.firstOrNull()?.note
                            ?: "Not a declared ingredient, but worth a look at the panel."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                foodDetail?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hardBlock) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.intake.badBg,
                    contentColor = MaterialTheme.intake.bad,
                    shape = IntakeShape.Tile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            IntakeIcons.ShieldAlert,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Save is blocked until you make a choice",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            scan.contains.forEach { match -> MatchDetail(match, MaterialTheme.intake.bad, ingredientText) }
            scan.mayContain.forEach { match -> MatchDetail(match, MaterialTheme.intake.warn, ingredientText) }

            if (!aiReasoning.isNullOrBlank()) {
                // SPEC §7.4: verbatim, because a restaurant dish is a judgment
                // call and the basis for it matters.
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = IntakeShape.Tile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Eyebrow("What the estimate said", color = MaterialTheme.intake.ai)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = aiReasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // The safe choice is the primary action, stacked above the danger action.
            Button(
                onClick = onDismiss,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Don't log it", style = MaterialTheme.typography.labelLarge) }

            Spacer(Modifier.height(8.dp))

            DangerButton(
                onClick = onLogAnyway,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Log it anyway", style = MaterialTheme.typography.labelLarge) }

            if (onFindSwap != null) {
                GhostButton(
                    onClick = {
                        // Closing without logging first, then opening Swap. Nothing is written on this path.
                        onDismiss()
                        onFindSwap()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Find a safer swap", style = MaterialTheme.typography.labelLarge) }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (hardBlock) {
                    "Only log it anyway if you have personally verified this food is safe for you."
                } else {
                    "This amber review is not a confirmed allergen block."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The 40 x 5 grabber of the mockup, in place of the stock drag handle. */
@Composable
private fun SheetGrabber() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

@Composable
private fun MatchDetail(
    match: AllergenMatch,
    accent: Color,
    ingredientText: String?,
) {
    Surface(
        shape = IntakeShape.Tile,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Eyebrow("exact match · ${MatchWording.foundIn(match)}", color = accent)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "“${match.matchedTerm}” · ${match.allergen}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = match.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Show the panel with the offending term picked out, as the mockup
            // does - seeing it in context is what makes the warning credible.
            if (!ingredientText.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Eyebrow("ingredients")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = highlight(ingredientText, match.matchedTerm, accent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Underlines every whole-word occurrence of [term] in [text]. */
private fun highlight(text: String, term: String, accent: Color): AnnotatedString {
    val haystack = text.lowercase()
    val needle = term.lowercase()
    return buildAnnotatedString {
        var cursor = 0
        while (true) {
            val index = haystack.indexOf(needle, cursor)
            if (index < 0 || needle.isEmpty()) {
                append(text.substring(cursor))
                return@buildAnnotatedString
            }
            append(text.substring(cursor, index))
            withStyle(
                SpanStyle(
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(text.substring(index, index + needle.length))
            }
            cursor = index + needle.length
        }
    }
}
