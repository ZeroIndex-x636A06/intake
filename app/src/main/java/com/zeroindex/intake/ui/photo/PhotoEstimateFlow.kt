package com.zeroindex.intake.ui.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zeroindex.intake.domain.ai.EstimateLine
import com.zeroindex.intake.domain.ai.VisionMath
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.MathGrid
import com.zeroindex.intake.ui.theme.AllergenAmber
import com.zeroindex.intake.ui.theme.AllergenRed
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.File
import kotlin.math.roundToInt

/**
 * The Photo tab of Add food: capture, estimate, review, confirm. SPEC §6.6:
 * nothing is written until the user confirms, and every number is editable.
 * Photo estimates are a fast first guess to correct, never a measurement, and
 * the screen says so.
 */
@Composable
fun PhotoEstimateFlow(
    slot: MealSlot,
    slotLabel: String,
    loggedAtMillis: Long,
    onOpenAiSettings: () -> Unit,
    onManualEntry: () -> Unit,
    onDone: () -> Unit,
    viewModel: PhotoEstimateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val stage = state.stage) {
        PhotoStage.NeedsKey -> Column(Modifier.padding(16.dp)) {
            AccentNote(
                lead = "Photo estimates are optional",
                body = "They use an AI model through your own OpenRouter key. Add one in Settings → AI; " +
                    "everything else in the app works without it.",
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenAiSettings, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Set up AI tools")
            }
        }

        PhotoStage.Capture -> Column {
            state.biasNote?.let {
                AccentNote(lead = "From your corrections", body = it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            PhotoCaptureView(newCaptureFile = viewModel::newCaptureFile, onPhoto = viewModel::onPhoto)
        }

        is PhotoStage.Working -> Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stage.message, style = MaterialTheme.typography.bodySmall)
        }

        is PhotoStage.Review -> Review(
            stage = stage,
            slotLabel = slotLabel,
            biasNote = state.biasNote,
            onScale = viewModel::setScale,
            onCautious = viewModel::useCautious,
            onEdit = viewModel::editLine,
            onRemove = viewModel::removeLine,
            onConfirm = { viewModel.confirm(slot, loggedAtMillis, onDone) },
            onRetake = viewModel::retake,
        )

        is PhotoStage.Unreadable -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The estimate came back in a form that could not be read", style = MaterialTheme.typography.titleMedium)
            Text(
                "Here is what the model said. Use it as a guide and log by hand.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stage.raw?.takeIf { it.isNotBlank() }?.let {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = IntakeShape.Tile) {
                    Text(it.take(2_000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
            Button(onClick = onManualEntry, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Log manually")
            }
            OutlinedButton(onClick = viewModel::retake, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Try another photo")
            }
        }

        PhotoStage.NotFood -> Column(Modifier.padding(16.dp)) {
            Text("That does not look like food", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Button(onClick = viewModel::retake, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Retake photo")
            }
        }

        is PhotoStage.Failed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stage.message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = viewModel::retake, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Try again")
            }
            OutlinedButton(onClick = onManualEntry, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Log manually")
            }
        }
    }

    (state.stage as? PhotoStage.Review)?.takeIf { it.confirmingAllergens }?.let { review ->
        AllergenWarningSheet(
            foodName = review.shown.joinToString(", ") { it.name },
            scan = review.result.allergens,
            onDismiss = viewModel::cancelAllergenConfirm,
            onLogAnyway = { viewModel.logAnyway(slot, loggedAtMillis, onDone) },
            foodDetail = "Photo estimate · ${VisionMath.total(review.shown).kcal.roundToInt()} kcal",
            aiReasoning = review.result.estimate.items.mapNotNull { it.allergenReasoning.takeIf(String::isNotBlank) }
                .joinToString(" ").ifBlank { null },
        )
    }
}

@Composable
private fun Review(
    stage: PhotoStage.Review,
    slotLabel: String,
    biasNote: String?,
    onScale: (Double) -> Unit,
    onCautious: () -> Unit,
    onEdit: (Int, EstimateLine) -> Unit,
    onRemove: (Int) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    val estimate = stage.result.estimate
    val range = estimate.range
    val shown = stage.shown
    val total = VisionMath.total(shown)

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Review estimate", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Nothing is saved until you confirm. Photo estimates are a first guess to correct.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AsyncImage(
            model = File(stage.result.photoPath),
            contentDescription = "The photo being estimated",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(18.dp)),
        )

        // SPEC §6.7: the portion assumption, prominent, with the slider beside it.
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = IntakeShape.Banner,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Portion assumption · please check", style = MaterialTheme.typography.titleSmall)
                Text(estimate.portionAssumption, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "Likely ${range.kcalLow.roundToInt()}–${range.kcalHigh.roundToInt()} kcal · " +
                        "${range.proteinLow.roundToInt()}–${range.proteinHigh.roundToInt()} g protein",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scale all portions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("%.1f×".format(stage.scale), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = stage.scale.toFloat(),
            onValueChange = { onScale((it * 10).roundToInt() / 10.0) },
            valueRange = VisionMath.MIN_SCALE.toFloat()..VisionMath.MAX_SCALE.toFloat(),
            steps = 14,
            modifier = Modifier.semantics { contentDescription = "Scale all estimated portions" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0.5×", "1.0×", "1.5×", "2.0×").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onCautious, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Use the cautious estimate (${range.kcalHigh.roundToInt()} kcal)")
        }

        biasNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }

        Eyebrow("Correct any field")
        shown.forEachIndexed { index, line ->
            LineEditor(
                line = line,
                flag = stage.result.flaggedLines[index],
                onChange = { onEdit(index, it) },
                onRemove = { onRemove(index) },
            )
        }

        if (estimate.hiddenCalorieSources.isNotEmpty()) {
            Eyebrow("Hidden calories counted")
            estimate.hiddenCalorieSources.forEach {
                Text("${it.source} · ${it.kcal.roundToInt()} kcal", style = MaterialTheme.typography.bodySmall)
            }
        }

        MathGrid(
            listOf(
                "${total.kcal.roundToInt()}" to "kcal",
                "${total.protein.roundToInt()}" to "protein g",
                "${total.carbs.roundToInt()}" to "carbs g",
                "${total.fat.roundToInt()}" to "fat g",
            ),
        )
        Text(
            "Estimated by ${stage.result.model}" +
                (stage.result.costUsd?.let { " · cost \$%.4f".format(it) } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConfirm,
            enabled = shown.isNotEmpty() && !stage.saving,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (stage.saving) "Saving…" else "Confirm & add to $slotLabel") }
        OutlinedButton(onClick = onRetake, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Retake photo")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LineEditor(line: EstimateLine, flag: AllergenMatchKind?, onChange: (EstimateLine) -> Unit, onRemove: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (flag != null) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = if (flag == AllergenMatchKind.CONTAINS) {
                        "Contains an allergen you avoid"
                    } else {
                        "May contain an allergen you avoid"
                    },
                    tint = if (flag == AllergenMatchKind.CONTAINS) AllergenRed else AllergenAmber,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            OutlinedTextField(
                value = line.name,
                onValueChange = { onChange(line.copy(name = it)) },
                singleLine = true,
                shape = IntakeShape.Field,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove ${line.name}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            NumberField("g", line.grams, Modifier.weight(1f)) { g ->
                // Changing the weight rescales that item's macros with it.
                val f = if (line.grams > 0) g / line.grams else 1.0
                onChange(line.scaled(f).copy(name = line.name, grams = g))
            }
            NumberField("kcal", line.kcal, Modifier.weight(1f)) { onChange(line.copy(kcal = it)) }
            NumberField("P", line.protein, Modifier.weight(1f)) { onChange(line.copy(protein = it)) }
            NumberField("C", line.carbs, Modifier.weight(1f)) { onChange(line.copy(carbs = it)) }
            NumberField("F", line.fat, Modifier.weight(1f)) { onChange(line.copy(fat = it)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun NumberField(label: String, value: Double, modifier: Modifier, onValue: (Double) -> Unit) {
    OutlinedTextField(
        value = value.roundToInt().toString(),
        onValueChange = { text -> onValue(text.filter(Char::isDigit).toDoubleOrNull() ?: 0.0) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = IntakeShape.ExtraSmall,
        modifier = modifier,
    )
}
