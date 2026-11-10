package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.domain.coach.DailyFocus
import com.zeroindex.intake.domain.nutrition.DayProgress
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.AnimatedBar
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.rememberSweepFraction
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.motion.pulseOn
import com.zeroindex.intake.ui.screens.today.NextMealUi
import com.zeroindex.intake.ui.screens.today.WorkoutCardState
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.ProteinAccent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Option A's calorie card: the ring with the kcal left, what was eaten against the target, then protein, fat and carbs
 * always visible (no "More detail"), then Log food with Scan and Photo beside it.
 */
@Composable
fun CalorieCard(
    progress: DayProgress,
    activityBonusKcal: Int,
    pulseTrigger: Int,
    onLogFood: () -> Unit,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    logModifier: Modifier = Modifier,
) {
    val target = progress.target
    val left = progress.kcalRemaining
    val fraction by rememberSweepFraction(progress.kcalFraction)
    val ringColor = if (progress.isOverKcal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    IntakeCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(150.dp).pulseOn(pulseTrigger).clearAndSetSemantics {
                    contentDescription = if (left == null) "Calories eaten ${progress.consumed.kcal.roundToInt()}. No target set." else
                        (if (left >= 0) "$left calories left of ${target?.kcal}" else "${abs(left)} calories over ${target?.kcal}")
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(150.dp)) {
                    val w = 14.dp.toPx()
                    val arc = Size(size.width - w, size.height - w)
                    drawArc(track, -90f, 360f, false, Offset(w / 2, w / 2), arc, style = Stroke(w))
                    drawArc(ringColor, -90f, 360f * fraction, false, Offset(w / 2, w / 2), arc, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(left?.let { "%,d".format(abs(it)) } ?: "%,d".format(progress.consumed.kcal.roundToInt()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text(if (left == null) "kcal eaten" else if (left >= 0) "kcal left" else "kcal over", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Eaten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%,d".format(progress.consumed.kcal.roundToInt()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(if (target != null) "of ${"%,d".format(target.kcal)} kcal" else "No target yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (activityBonusKcal > 0) {
                    Text("Includes +$activityBonusKcal kcal from workouts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MacroTile("Protein", progress.consumed.protein, target?.proteinG, progress.proteinFraction, ProteinAccent, Modifier.weight(1f))
            MacroTile("Fat", progress.consumed.fat, target?.fatG, progress.fatFraction, FatAccent, Modifier.weight(1f))
            MacroTile("Carbs", progress.consumed.carbs, target?.carbsG, progress.carbsFraction, CarbAccent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onLogFood, modifier = logModifier.weight(1f).heightIn(min = 52.dp).pressScale(), shape = RoundedCornerShape(26.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log food")
            }
            FilledTonalIconButton(onClick = onScan, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan a barcode") }
            FilledTonalIconButton(onClick = onPhoto, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.CameraAlt, contentDescription = "Estimate from a photo") }
        }
    }
}

@Composable
private fun MacroTile(label: String, eaten: Double, targetG: Int?, fraction: Float, color: Color, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp).clearAndSetSemantics {
            contentDescription = "$label ${eaten.roundToInt()} of ${targetG ?: "no target"} grams"
        }) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${eaten.roundToInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
                Text(" / ${targetG ?: "—"} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            AnimatedBar(fraction, color, MaterialTheme.colorScheme.surfaceVariant, height = 6.dp)
        }
    }
}

/**
 * Next up: the coach's one thing, the planned or prepped meal with Swap and Log it, and the next workout with Start.
 * Hidden when there is nothing to show. An allergen match still blocks Log it (the repository refuses it).
 */
@Composable
fun NextUpCard(
    meal: NextMealUi?,
    workout: WorkoutCardState,
    focus: DailyFocus?,
    showMeal: Boolean,
    onSwap: () -> Unit,
    onLogMeal: (NextMealUi) -> Unit,
    onOpenPlan: () -> Unit,
    onFocusAction: () -> Unit,
    onStart: () -> Unit,
    onResume: (String) -> Unit,
    onMakeProgram: () -> Unit,
    modifier: Modifier = Modifier,
    focusModifier: Modifier = Modifier,
    workoutModifier: Modifier = Modifier,
) {
    val mealShown = showMeal && meal != null
    val workoutShown = workout !is WorkoutCardState.Loading
    if (!mealShown && !workoutShown && focus == null) return
    IntakeCard(modifier) {
        Eyebrow("Next up")
        Spacer(Modifier.height(8.dp))
        if (focus != null) {
            Column(focusModifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(focus.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    StatusPill(if (focus.onTrack) "On track" else "Today", tone = if (focus.onTrack) PillTone.Ok else PillTone.Wait)
                }
                Text(focus.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (focus.buttonLabel != null) {
                    OutlinedButton(onClick = onFocusAction, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) { Text(focus.buttonLabel) }
                }
            }
            if (mealShown || workoutShown) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
        }
        if (mealShown && meal != null) {
            val m = meal.content.macros.perServing
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${meal.slot.name} · planned".uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        when (meal.content.allergenStatus) {
                            AllergenStatus.CONTAINS -> StatusPill("Contains your allergen", tone = PillTone.Bad)
                            AllergenStatus.MAY_CONTAIN -> StatusPill("May contain", tone = PillTone.Bad)
                            AllergenStatus.CHECK_LABEL -> StatusPill("Check the label", tone = PillTone.Wait)
                            AllergenStatus.UNKNOWN -> StatusPill("Not checked", tone = PillTone.Wait)
                            else -> StatusPill("No allergens", tone = PillTone.Ok)
                        }
                    }
                    Text(meal.content.recipe.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (meal.content.macros.isComplete) "${m.kcal.roundToInt()} kcal · P ${m.protein.roundToInt()} · C ${m.carbs.roundToInt()} · F ${m.fat.roundToInt()}" else "Macros incomplete. Open the plan to confirm.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSwap, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Swap")
                        }
                        Button(
                            onClick = { if (meal.content.macros.isComplete) onLogMeal(meal) else onOpenPlan() },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp),
                        ) { Text(if (meal.content.macros.isComplete) "Log it" else "Open plan") }
                    }
                }
            }
            if (workoutShown) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
        }
        if (workoutShown) {
            Row(workoutModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(12.dp))
                val (title, sub, action) = workoutLine(workout)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (workout) {
                    is WorkoutCardState.InProgress -> Button(onClick = { onResume(workout.sessionId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
                    WorkoutCardState.NoProgram -> OutlinedButton(onClick = onMakeProgram, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
                    else -> Button(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
                }
            }
        }
    }
}

private fun workoutLine(w: WorkoutCardState): Triple<String, String, String> = when (w) {
    is WorkoutCardState.Next -> Triple(w.dayName, "${w.programName} · day ${w.dayNumber} of ${w.daysInProgram}", "Start")
    is WorkoutCardState.Rest -> Triple("Rest day", "Next: ${w.nextDayName}" + if (w.daysAway == 1) ", tomorrow" else "", "Train anyway")
    is WorkoutCardState.InProgress -> Triple("Workout in progress", w.programName ?: "", "Resume")
    WorkoutCardState.NoProgram -> Triple("No program yet", "A few questions and Intake builds one", "Make one")
    WorkoutCardState.Loading -> Triple("", "", "")
}

/** The weight tile: the last weigh-in, its change this week, and a Weigh in button. */
@Composable
fun WeightTile(weightKg: Double?, deltaKg: Double?, unit: WeightUnit, onWeighIn: () -> Unit, modifier: Modifier = Modifier) {
    IntakeCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(weightKg?.let { "%.1f %s".format(unit.fromKg(it), unit.label) } ?: "No weigh-in yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                deltaKg?.let {
                    val v = unit.fromKg(it)
                    Text((if (v >= 0) "+" else "−") + "%.1f %s this week".format(abs(v), unit.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Button(onClick = onWeighIn, modifier = Modifier.heightIn(min = 48.dp)) { Text("Weigh in") }
        }
    }
}
