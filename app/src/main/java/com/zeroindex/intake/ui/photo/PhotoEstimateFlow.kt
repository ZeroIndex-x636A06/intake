@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.zeroindex.intake.domain.ai.HiddenCalorieRules
import com.zeroindex.intake.domain.ai.PhotoItem
import com.zeroindex.intake.domain.ai.PhotoItems
import com.zeroindex.intake.domain.ai.VisionMath
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.AiWaitCard
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.MathGrid
import com.zeroindex.intake.ui.theme.AllergenAmber
import com.zeroindex.intake.ui.theme.AllergenRed
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Photo tab of Add food: capture, estimate, review, confirm. SPEC §6.6:
 * nothing is written until the user confirms, and every number is editable.
 * Photo estimates are a fast first guess to correct, never a measurement, and
 * the screen says so.
 *
 * One photo returns a list of foods. Each can be unticked, renamed and resized, and one tap logs every ticked
 * food to the meal, each as its own entry. Portions follow the person's unit setting.
 */
@Composable
fun PhotoEstimateFlow(
    slot: MealSlot,
    slotLabel: String,
    loggedAtMillis: Long,
    onOpenAiSettings: () -> Unit,
    onManualEntry: () -> Unit,
    onDone: () -> Unit,
    /** Opens Log food's search with this product name filled in: a label beats a guess. */
    onFindProduct: (String) -> Unit = {},
    viewModel: PhotoEstimateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val stage = state.stage) {
        PhotoStage.NeedsKey -> Column(Modifier.padding(16.dp)) {
            AccentNote(
                lead = "Photo estimates are optional",
                body = "They use an AI model through your own OpenRouter key and need “Use AI features” on. " +
                    "Set both in Settings → AI; everything else in the app works without it.",
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
            OutlinedTextField(
                value = state.extraContext,
                onValueChange = viewModel::setContext,
                label = { Text("Anything we can't see?") },
                placeholder = { Text("e.g. cooked in butter, large portion, sauce on the side") },
                supportingText = { Text("Optional. Sent to OpenRouter with the photo.") },
                minLines = 2,
                maxLines = 3,
                shape = IntakeShape.Field,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            PhotoCaptureView(newCaptureFile = viewModel::newCaptureFile, onPhoto = viewModel::onPhoto)
        }

        is PhotoStage.Working -> AiWaitCard(
            message = stage.message,
            startedAtMillis = stage.startedAt,
            onCancel = viewModel::cancel,
        )

        is PhotoStage.Review -> Review(
            stage = stage,
            unit = state.unit,
            slotLabel = slotLabel,
            biasNote = state.biasNote,
            onScale = viewModel::setScale,
            onCautious = viewModel::useCautious,
            onToggle = viewModel::toggle,
            onRename = viewModel::rename,
            onPortion = viewModel::setPortion,
            onMacro = viewModel::setMacro,
            onConfirm = { viewModel.confirm(slot, loggedAtMillis, state.unit, onDone) },
            onRetake = viewModel::retake,
            onAnswer = viewModel::answer,
            onRefine = viewModel::refine,
            onCancelRefine = viewModel::cancelRefine,
            onFindProduct = onFindProduct,
        )

        // Never the model's own text: a plain message and a way forward.
        is PhotoStage.Unreadable -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The estimate could not be read", style = MaterialTheme.typography.titleMedium)
            Text(
                stage.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewModel::retake, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Try again")
            }
            OutlinedButton(onClick = onManualEntry, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Log manually")
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

    (state.stage as? PhotoStage.Review)?.confirmingAllergens?.let { scan ->
        val review = state.stage as PhotoStage.Review
        AllergenWarningSheet(
            foodName = review.shown.joinToString(", ") { it.line.name },
            scan = scan,
            onDismiss = viewModel::cancelAllergenConfirm,
            onLogAnyway = { viewModel.logAnyway(slot, loggedAtMillis, state.unit, onDone) },
            foodDetail = "Photo estimate · ${PhotoItems.total(review.items, review.scale).kcal.roundToInt()} kcal",
            aiReasoning = review.result.estimate.items.mapNotNull { it.allergenReasoning.takeIf(String::isNotBlank) }
                .joinToString(" ").ifBlank { null },
        )
    }
}

@Composable
private fun Review(
    stage: PhotoStage.Review,
    unit: PortionUnit,
    slotLabel: String,
    biasNote: String?,
    onScale: (Double) -> Unit,
    onCautious: () -> Unit,
    onToggle: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onPortion: (Int, String) -> Unit,
    onMacro: (Int, (EstimateLine) -> EstimateLine) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onAnswer: (Int, String) -> Unit,
    onRefine: () -> Unit,
    onCancelRefine: () -> Unit,
    onFindProduct: (String) -> Unit,
) {
    val estimate = stage.result.estimate
    val range = estimate.range
    val total = PhotoItems.total(stage.items, stage.scale)
    val ticked = stage.ticked

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

        stage.result.productQuery?.let { product ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = IntakeShape.Banner,
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This looks like a packaged product", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$product. The label on the pack is more exact than any estimate from a photo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { onFindProduct(product) },
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Find this product instead") }
                }
            }
        }

        if (stage.questions.isNotEmpty()) {
            Questions(stage, onAnswer, onRefine, onCancelRefine)
        }

        // SPEC §6.7: the portion assumption, prominent, with the slider beside it.
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = IntakeShape.Banner,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Portion assumption · please check", style = MaterialTheme.typography.titleSmall)
                Text(estimate.portionAssumption, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Whole plate likely ${range.kcalLow.roundToInt()}–${range.kcalHigh.roundToInt()} kcal, " +
                        "${range.proteinLow.roundToInt()}–${range.proteinHigh.roundToInt()} g protein",
                    style = MaterialTheme.typography.labelLarge,
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
        OutlinedButton(
            onClick = onCautious,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Use the cautious estimate (${range.kcalHigh.roundToInt()} kcal)")
        }

        biasNote?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }

        Eyebrow("${stage.items.size} ${if (stage.items.size == 1) "food" else "foods"} found")
        Text(
            "Untick anything that is not yours to log. Edit a name or a portion and the numbers follow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        stage.items.forEach { item ->
            val modelItem = estimate.items.getOrNull(item.id)
            FoodItemCard(
                item = item,
                shownGrams = item.line.grams * stage.scale,
                unit = unit,
                scale = stage.scale,
                flag = stage.result.flaggedLines[item.id],
                allergens = modelItem?.containsAllergens.orEmpty(),
                reasoning = modelItem?.allergenReasoning.orEmpty(),
                onToggle = { onToggle(item.id) },
                onRename = { onRename(item.id, it) },
                onPortion = { onPortion(item.id, it) },
                onMacro = { change -> onMacro(item.id, change) },
            )
        }

        val counted = estimate.hiddenCalorieSources.filter { HiddenCalorieRules.isCounted(it.basis) }
        if (counted.isNotEmpty()) {
            Eyebrow("Hidden calories counted")
            counted.forEach {
                Text("${it.source} · ${it.kcal.roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Eyebrow(if (ticked == stage.items.size) "Total" else "Total of $ticked ticked")
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
                (stage.result.costUsd?.let { " · cost \$%.4f".format(it) } ?: "") +
                (if (stage.result.calls > 1) " · ${stage.result.calls} calls" else ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConfirm,
            enabled = ticked > 0 && !stage.saving,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(
                when {
                    stage.saving -> "Saving…"
                    ticked == 1 -> "Log 1 food to $slotLabel"
                    else -> "Log $ticked foods to $slotLabel"
                },
            )
        }
        OutlinedButton(onClick = onRetake, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Text("Retake photo")
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * "Ask instead of guessing wrong": the model's short questions as tappable choices, above the estimate. The answers
 * go back in one follow-up call for the final numbers; nothing is sent until the button is pressed.
 */
@Composable
private fun Questions(
    stage: PhotoStage.Review,
    onAnswer: (Int, String) -> Unit,
    onRefine: () -> Unit,
    onCancelRefine: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = IntakeShape.Banner,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("A quick check", style = MaterialTheme.typography.titleSmall)
            Text(
                "The model is not sure about a few things. Tap what fits for a better estimate, or skip them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stage.questions.forEachIndexed { index, q ->
                Text(q.text, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    q.choices.forEach { choice ->
                        ChoiceChip(choice, stage.answers[index] == choice, { onAnswer(index, choice) })
                    }
                }
            }
            if (stage.refining) {
                Text("Working out the final numbers…", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onCancelRefine) { Text("Cancel") }
            } else {
                Button(
                    onClick = onRefine,
                    enabled = stage.canRefine,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Update the estimate with my answers") }
                Text(
                    "One more call to the model, without the photo. It replaces any edits you made below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            stage.refineError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * One food: a tick, a name that wraps, the portion in the person's unit with the model's range beside it, the
 * macros as text (editable behind a tap, in a 2 by 2 grid wide enough to read), and any allergen note. Nothing is
 * set in a fixed-width row of five fields, which is what clipped the old layout.
 */
@Composable
private fun FoodItemCard(
    item: PhotoItem,
    shownGrams: Double,
    unit: PortionUnit,
    scale: Double,
    flag: AllergenMatchKind?,
    allergens: List<String>,
    reasoning: String,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onPortion: (String) -> Unit,
    onMacro: ((EstimateLine) -> EstimateLine) -> Unit,
) {
    var editingMacros by remember { mutableStateOf(false) }
    val line = item.line.scaled(scale)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = item.included,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = "Log ${item.line.name}" },
            )
            OutlinedTextField(
                value = item.line.name,
                onValueChange = onRename,
                label = { Text("Food") },
                minLines = 1,
                maxLines = 3,
                enabled = item.included,
                shape = IntakeShape.Field,
                modifier = Modifier.weight(1f),
            )
        }
        if (item.included) {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PortionField(shownGrams = shownGrams, unit = unit, onChange = onPortion, modifier = Modifier.width(130.dp))
                    val low = item.gramsLow
                    val high = item.gramsHigh
                    Text(
                        if (low != null && high != null && high > low) {
                            "Likely ${unit.formatRange(low * scale, high * scale)}"
                        } else {
                            "Confidence: ${item.confidence}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${line.kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleSmall)
                    Text("Protein ${line.protein.roundToInt()} g", style = MaterialTheme.typography.bodyMedium)
                    Text("Carbs ${line.carbs.roundToInt()} g", style = MaterialTheme.typography.bodyMedium)
                    Text("Fat ${line.fat.roundToInt()} g", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { editingMacros = !editingMacros }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(if (editingMacros) "Done editing numbers" else "Edit calories and macros")
                }
                if (editingMacros) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField("Calories", line.kcal, Modifier.weight(1f)) { v -> onMacro { it.copy(kcal = v) } }
                        NumberField("Protein (g)", line.protein, Modifier.weight(1f)) { v -> onMacro { it.copy(protein = v) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NumberField("Carbs (g)", line.carbs, Modifier.weight(1f)) { v -> onMacro { it.copy(carbs = v) } }
                        NumberField("Fat (g)", line.fat, Modifier.weight(1f)) { v -> onMacro { it.copy(fat = v) } }
                    }
                }
                // The soft "check the label" tier is not a warning, so it gets no icon here.
                if (flag != null && flag != AllergenMatchKind.CHECK_LABEL) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (flag == AllergenMatchKind.CONTAINS) AllergenRed else AllergenAmber,
                        )
                        Text(
                            buildString {
                                append(
                                    if (flag == AllergenMatchKind.CONTAINS) "Contains something you avoid" else "May contain something you avoid",
                                )
                                if (allergens.isNotEmpty()) append(": ${allergens.joinToString()}")
                                if (reasoning.isNotBlank()) append(". $reasoning")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The portion as typed in the person's unit; the text is kept while typing and re-synced when the slider moves it. */
@Composable
private fun PortionField(shownGrams: Double, unit: PortionUnit, onChange: (String) -> Unit, modifier: Modifier) {
    var text by remember(unit) { mutableStateOf(unit.number(shownGrams)) }
    LaunchedEffect(shownGrams, unit) {
        val typed = unit.parseToGrams(text)
        if (typed == null || abs(typed - shownGrams) > 0.5) text = unit.number(shownGrams)
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text("Portion (${unit.label})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = IntakeShape.Field,
        modifier = modifier,
    )
}

@Composable
private fun NumberField(label: String, value: Double, modifier: Modifier, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(value.roundToInt().toString()) }
    LaunchedEffect(value) {
        if ((text.toDoubleOrNull() ?: -1.0).roundToInt() != value.roundToInt()) text = value.roundToInt().toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.filter { it.isDigit() || it == '.' }
            onValue(text.toDoubleOrNull() ?: 0.0)
        },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = IntakeShape.Field,
        modifier = modifier,
    )
}
