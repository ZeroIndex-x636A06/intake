@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.AllergenMatchKind

/**
 * SPEC §7.2: a full-width red confirmation sheet naming the exact matched term
 * and where it was found, with an explicit "log it anyway" tap. Never a toast,
 * never a small banner.
 *
 * SPEC §7.3 splits the two severities: a declared "contains" is red, a "may
 * contain" is amber. Each carries a word as well as a colour, since colour is
 * never the only signal (SPEC §3.7).
 */
@Composable
fun AllergenWarningSheet(
    foodName: String,
    scan: AllergenScanResult,
    onDismiss: () -> Unit,
    onLogAnyway: () -> Unit,
    /** SPEC §7.4: the model's own reasoning, shown verbatim for AI results. */
    aiReasoning: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hardBlock = scan.blocks
    val accent = if (hardBlock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val onAccent = if (hardBlock) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onTertiary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Full-width banner, not a small inline chip.
            Surface(color = accent, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(
                            text = if (hardBlock) {
                                "Contains something you avoid"
                            } else {
                                "Might contain it"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = onAccent,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = foodName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onAccent,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (scan.contains.isNotEmpty()) {
                    MatchGroup("Contains", scan.contains, MaterialTheme.colorScheme.error)
                }
                if (scan.mayContain.isNotEmpty()) {
                    MatchGroup("May contain", scan.mayContain, MaterialTheme.colorScheme.tertiary)
                }

                if (!aiReasoning.isNullOrBlank()) {
                    // SPEC §7.4: displayed verbatim, because a restaurant dish
                    // is a judgment call and the basis for it matters.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = "What the estimate said",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = aiReasoning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("Don't log it") }

                Button(
                    onClick = onLogAnyway,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = onAccent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("Log it anyway") }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MatchGroup(title: String, matches: List<AllergenMatch>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = accent)
        matches.forEach { match ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(14.dp),
            ) {
                Text(
                    text = "\"${match.matchedTerm}\" · ${match.allergen}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = match.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (match.kind == AllergenMatchKind.MAY_CONTAIN) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Softer warning: not a declared ingredient.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
