package com.zeroindex.intake.ui.screens.allergen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.ui.components.ScreenHeading
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * SPEC §7.6: the hidden-allergen checklist and the per-venue eat-out notes,
 * reachable in two taps and loaded entirely from local data - this gets read
 * standing at a counter, so it never waits on a network call.
 */
@Composable
fun AllergenReferenceScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: AllergenReferenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "heading") {
            Column {
                ScreenHeading("Hidden allergens")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.avoided.isEmpty()) {
                        "No allergens set yet. Add them in Settings and every food that " +
                            "enters the app gets checked against this list."
                    } else {
                        "Avoiding ${state.avoided.joinToString(", ")}. Every one of these " +
                            "terms means it is present, whatever the front of the pack says."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.aliasesByAllergen.forEach { (allergen, terms) ->
            item(key = "allergen-$allergen") {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IntakeShape.Card,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = allergen.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${terms.size} terms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        terms.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pair.forEach { term ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = term,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 8.dp,
                                            ),
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (state.eatOutOptions.isNotEmpty()) {
            item(key = "eatout-heading") {
                ScreenHeading("Eating out")
            }
            items(items = state.eatOutOptions, key = { it.id }) { option ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IntakeShape.Card,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(option.venueName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = option.whatToOrder,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (option.cautionNote.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = option.cautionNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "About ${option.estimatedKcal} kcal · " +
                                "${option.estimatedProtein} g protein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
