package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.training.TrainingCalculators
import com.zeroindex.intake.domain.units.WeightText
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.theme.IntakeSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The calculators only need the person's units, bar, plates and 1RM formula. */
@HiltViewModel
class CalculatorsViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}

/** "What is my max?", "what is 80% of it?" and "what goes on the bar?", in the person's unit. */
@Composable
fun CalculatorsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: CalculatorsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val unit = s.weightUnit
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = IntakeSpacing.ScreenGutter + contentPadding.calculateStartPadding(LayoutDirection.Ltr),
            end = IntakeSpacing.ScreenGutter + contentPadding.calculateEndPadding(LayoutDirection.Ltr),
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item { OneRepMaxAndPercentages(s, unit) }
        item { PlateLoader(s, unit) }
    }
}

/** What was typed, as a number: a comma is accepted as the decimal mark. */
private fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

@Composable
private fun OneRepMaxAndPercentages(s: AppSettings, unit: WeightUnit) {
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var known by rememberSaveable { mutableStateOf("") }
    val repsInt = reps.trim().toIntOrNull()
    val estimateKg = TrainingCalculators.estimate(parse(weight)?.let(unit::toKg), repsInt)
    val knownKg = parse(known)?.takeIf { it > 0 }?.let(unit::toKg)
    val baseKg = knownKg ?: estimateKg

    Column(verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap)) {
    SectionCard(title = "Estimated 1RM", caption = "weight x reps") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LabeledField("Weight lifted (${unit.label})", weight, { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            LabeledField("Reps", reps, { reps = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
        }
        if (estimateKg != null) {
            Text(
                WeightText.format(estimateKg, unit),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text("estimated one-rep max", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "Enter the weight and the reps of a hard set (1 to ${TrainingCalculators.MAX_REPS} reps).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TrainingCalculators.caution(repsInt)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${s.oneRepMaxFormula.description}. Change the formula in Settings > Training.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    SectionCard(title = "Percentages of your max", caption = "50% to 100%") {
        LabeledField(
            "Your 1RM (${unit.label}), optional", known, { known = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            keyboardType = KeyboardType.Decimal,
            supporting = "Leave empty to use the estimate above.",
        )
        val rows = TrainingCalculators.percentTable(baseKg)
        if (rows.isEmpty()) {
            Text(
                "Work out an estimate above or type your max, and the table appears here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            rows.forEachIndexed { i, row ->
                if (i > 0) ListDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("${row.percent}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(WeightText.format(row.weightKg, unit), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
    }
}

@Composable
private fun PlateLoader(s: AppSettings, unit: WeightUnit) {
    var target by rememberSaveable { mutableStateOf("") }
    val targetKg = parse(target)?.takeIf { it > 0 }?.let(unit::toKg)
    SectionCard(title = "Plate loader", caption = "per side of the bar") {
        LabeledField(
            "Total weight on the bar (${unit.label})", target, { target = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            keyboardType = KeyboardType.Decimal,
            supporting = "${TrainingCalculators.barText(s.barWeightKg, unit)}. Change the bar and plates in Settings > Training.",
        )
        if (targetKg != null) {
            val load = TrainingCalculators.plates(targetKg, s.barWeightKg, s.platesKgList)
            Text(
                TrainingCalculators.platesText(load, unit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Loads to ${WeightText.format(load.totalKg, unit)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
