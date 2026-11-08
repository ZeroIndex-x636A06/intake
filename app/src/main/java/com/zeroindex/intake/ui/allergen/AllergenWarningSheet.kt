@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.theme.AllergenAmber
import com.zeroindex.intake.ui.theme.AllergenRed
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * SPEC §7.2: a full-width red confirmation sheet naming the exact matched term
 * and where it was found, with an explicit "log it anyway" tap. Never a toast,
 * never a small banner.
 *
 * Styled from design/intake-mockup.html, which makes one change worth calling
 * out: **"Don't log it" is the filled, primary button and "Log it anyway" is
 * the quiet outline.** The spec only requires the override to be explicit; the
 * mockup is right that the safe choice should also be the easy one.
 *
 * SPEC §7.3 keeps the two severities visibly apart - a declared "contains" is
 * red, a "may contain" amber - and each carries words as well as colour, since
 * colour is never the only signal (SPEC §3.7).
 */
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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hardBlock = scan.blocks
    val accent = if (hardBlock) AllergenRed else AllergenAmber

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = if (hardBlock) "Food safety check" else "Check this item",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
            )

            // Full-width banner, not a small inline chip.
            Surface(color = accent, contentColor = Color.White, shape = IntakeShape.Banner) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White.copy(alpha = 0.19f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (hardBlock) {
                                    Icons.Filled.Warning
                                } else {
                                    Icons.Filled.PriorityHigh
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(7.dp),
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Eyebrow(
                            text = if (hardBlock) {
                                "Save blocked · ${scan.contains.first().allergen} detected"
                            } else {
                                "May contain · soft match"
                            },
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    Text(
                        text = if (hardBlock) {
                            "This contains an ingredient you avoid"
                        } else {
                            "Possible cross-contact"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (hardBlock) {
                            "Your allergen profile matched an ingredient. The food cannot be " +
                                "saved until you choose what to do."
                        } else {
                            scan.mayContain.firstOrNull()?.note
                                ?: "Not a declared ingredient, but worth a look at the panel."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // The food itself, with an unmistakable "not saved" state.
            Surface(
                shape = IntakeShape.InnerCard,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(foodName, style = MaterialTheme.typography.titleSmall)
                        foodDetail?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Eyebrow(
                        text = if (hardBlock) "not saved" else "review",
                        color = accent,
                    )
                }
            }

            if (hardBlock) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = IntakeShape.Tile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
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

            scan.contains.forEach { match -> MatchDetail(match, AllergenRed, ingredientText) }
            scan.mayContain.forEach { match -> MatchDetail(match, AllergenAmber, ingredientText) }

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
                        Eyebrow("What the estimate said")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = aiReasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // The safe choice is the primary action.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    shape = IntakeShape.Field,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f).height(54.dp),
                ) { Text("Don't log it", style = MaterialTheme.typography.labelLarge) }

                OutlinedButton(
                    onClick = onLogAnyway,
                    shape = IntakeShape.Field,
                    modifier = Modifier.weight(1f).height(54.dp),
                ) { Text("Log it anyway", style = MaterialTheme.typography.labelLarge) }
            }

            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(18.dp))
        }
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
            Eyebrow("exact match found", color = accent)
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
