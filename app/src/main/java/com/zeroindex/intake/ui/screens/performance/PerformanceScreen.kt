package com.zeroindex.intake.ui.screens.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.performance.JumpMetrics
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.FormRow
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Amendment §2 entry form. Enter any two of vertical, standing and total
 * reach; the third is shown as it is worked out, so it is obvious which
 * number was measured and which was derived.
 */
@Composable
fun PerformanceScreen(
    contentPadding: PaddingValues,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = state.settings.lengthUnit
    var vertical by remember { mutableStateOf("") }
    var standing by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val preview = JumpMetrics.complete(vertical.toDoubleOrNull(), standing.toDoubleOrNull(), total.toDoubleOrNull())
    val dates = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            SubHead("Explosive performance", "Track a jump test in ${if (unit == LengthUnit.INCHES) "inches" else "centimetres"}. Enter any two; the third is worked out.")
        }
        item(key = "form") {
            IntakeCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormRow(
                        left = { m ->
                            Field(vertical, { vertical = it }, "Vertical jump · ${unit.label}",
                                derived = preview.derived == JumpMetrics.Field.VERTICAL, derivedValue = preview.verticalJumpCm, modifier = m)
                        },
                        right = { m ->
                            Field(standing, { standing = it }, "Standing reach · ${unit.label}",
                                derived = preview.derived == JumpMetrics.Field.STANDING, derivedValue = preview.standingReachCm, modifier = m)
                        },
                    )
                    Field(total, { total = it }, "Total jumping reach · ${unit.label}",
                        derived = preview.derived == JumpMetrics.Field.TOTAL, derivedValue = preview.totalReachCm)
                    if (preview.inconsistent) {
                        ErrorState(
                            title = "These do not add up",
                            body = "Total reach should be standing reach plus the jump. Check one of them before saving.",
                        )
                    }
                    LabeledField(
                        label = "Note · optional",
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = "Shoes, surface, warm-up…",
                    )
                    Text(
                        "Jump height = total jumping reach − standing reach.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.error?.let { ErrorState(body = it) }
                    Button(
                        onClick = {
                            viewModel.save(
                                vertical.toDoubleOrNull(),
                                standing.toDoubleOrNull(),
                                total.toDoubleOrNull(),
                                notes.ifBlank { null },
                            ) {
                                vertical = ""; standing = ""; total = ""; notes = ""
                                onSaved()
                            }
                        },
                        enabled = !preview.inconsistent &&
                            (preview.verticalJumpCm != null || preview.totalReachCm != null),
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Log jump test") }
                }
            }
        }
        item(key = "tip") {
            AccentNote(
                lead = "Test the same way each time",
                body = "Same surface, same warm-up, best of three. Jump height moves a few " +
                    "percent day to day, so the trend matters more than any single test.",
            )
        }
        if (state.history.isEmpty()) {
            item(key = "empty") { EmptyState(title = "No jump tests yet", body = "Fill in the boxes above and tap Log jump test. The first one is your baseline.") }
        } else {
            item(key = "history-head") { SectionLine("History", note = "Trend is on Progress") }
            items(state.history, key = { it.id }) { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = dates.format(Instant.ofEpochMilli(m.measuredAt).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = listOfNotNull(
                                m.verticalJumpCm?.let { "jump ${fmt(unit, it)}" },
                                m.standingReachCm?.let { "standing ${fmt(unit, it)}" },
                                m.totalReachCm?.let { "total ${fmt(unit, it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.delete(m.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete this jump test")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    derived: Boolean,
    /** Already in the display unit: it is derived from what was typed. */
    derivedValue: Double?,
    modifier: Modifier = Modifier,
) {
    val computed = derived && value.isBlank()
    LabeledField(
        label = if (computed) "$label · computed" else label,
        value = if (computed) derivedValue?.let { "%.1f".format(it) }.orEmpty() else value,
        onValueChange = { next -> onChange(next.filter { it.isDigit() || it == '.' }) },
        readOnly = computed,
        keyboardType = KeyboardType.Decimal,
        modifier = modifier,
    )
}

private fun fmt(unit: LengthUnit, cm: Double): String =
    "${UnitConverter.round(unit.fromCm(cm), 1)} ${unit.label}"
