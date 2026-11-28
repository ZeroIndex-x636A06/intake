package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.domain.coach.DailyFocus
import com.zeroindex.intake.domain.nutrition.DayProgress
import com.zeroindex.intake.domain.units.KcalFormat
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.AccentButton
import com.zeroindex.intake.ui.components.AnimatedBar
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.IconTileStyle
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.components.rememberSweepFraction
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.motion.pulseOn
import com.zeroindex.intake.ui.navigation.LocalSessionBarSpace
import com.zeroindex.intake.ui.screens.today.NextMealUi
import com.zeroindex.intake.ui.screens.today.WorkoutCardState
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.SpaceGrotesk
import com.zeroindex.intake.ui.theme.intake
import kotlin.math.abs
import kotlin.math.roundToInt

/** The hero card's size with nothing in it, shown until the first database read so no false "0 kcal" or "No target yet" appears. */
@Composable
fun CalorieCardSkeleton(modifier: Modifier = Modifier) {
    HeroCard(modifier) {
        Box(Modifier.fillMaxWidth().height(CalorieCardSkeletonHeight))
    }
}

private val CalorieCardSkeletonHeight = 234.dp

/**
 * The dark hero card (`.card.inkc`): the ring with the kcal left, protein, carbs and fat as bars beside it, the eaten and
 * target line, then Log food with Scan and Photo beside it. The macros are always visible. The mockup's "More detail" link
 * has no screen behind it today, so it is left out.
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
    val c = MaterialTheme.intake
    val ringColor = if (progress.isOverKcal) c.warn else c.accent
    val track = c.onHero.copy(alpha = 0.14f)
    val muted = heroMuted()
    HeroCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(132.dp).pulseOn(pulseTrigger).clearAndSetSemantics {
                    contentDescription = if (left == null) "Calories eaten ${KcalFormat.format(progress.consumed.kcal)}. No target set." else
                        (if (left >= 0) "${KcalFormat.format(left)} calories left of ${target?.kcal?.let { KcalFormat.format(it) }}" else "${KcalFormat.format(abs(left))} calories over ${target?.kcal?.let { KcalFormat.format(it) }}")
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(132.dp)) {
                    val w = 12.dp.toPx()
                    val arc = Size(size.width - w, size.height - w)
                    drawArc(track, -90f, 360f, false, Offset(w / 2, w / 2), arc, style = Stroke(w))
                    drawArc(ringColor, -90f, 360f * fraction, false, Offset(w / 2, w / 2), arc, style = Stroke(w, cap = StrokeCap.Round))
                }
                // The figure is capped at 1.2x so it stays inside the ring at the largest font scale.
                val fontScale = LocalDensity.current.fontScale
                val figureSize = (30f * minOf(fontScale, 1.2f) / fontScale).sp
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        left?.let { KcalFormat.format(abs(it)) } ?: KcalFormat.format(progress.consumed.kcal),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = figureSize, lineHeight = figureSize * 1.1f),
                        maxLines = 1, softWrap = false,
                    )
                    Text(if (left == null) "kcal eaten" else if (left >= 0) "kcal left" else "kcal over", style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroBar("Protein", progress.consumed.protein, target?.proteinG, progress.proteinFraction, c.protein)
                MacroBar("Carbs", progress.consumed.carbs, target?.carbsG, progress.carbsFraction, c.carb)
                MacroBar("Fat", progress.consumed.fat, target?.fatG, progress.fatFraction, c.fat)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (target != null) "Eaten ${KcalFormat.format(progress.consumed.kcal)} · Target ${KcalFormat.format(target.kcal)}" else "Eaten ${KcalFormat.format(progress.consumed.kcal)} · No target yet",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        if (activityBonusKcal > 0) {
            Text("Includes +$activityBonusKcal kcal from workouts", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentButton(onClick = onLogFood, modifier = logModifier.weight(1f).pressScale()) {
                Text("Log food")
            }
            HeroIconButton(IntakeIcons.Scan, "Scan a barcode", onScan)
            HeroIconButton(IntakeIcons.Camera, "Estimate from a photo", onPhoto)
        }
    }
}

/** A 44 dp circle on the hero card: onHero at 12% with an onHero icon (`.iconbtn` inside `.inkc`). */
@Composable
private fun HeroIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val onHero = MaterialTheme.intake.onHero
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(IntakeSpacing.TouchTarget),
        colors = IconButtonDefaults.iconButtonColors(containerColor = onHero.copy(alpha = 0.12f), contentColor = onHero),
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(IconSize.Default)) }
}

/** One macro on the hero card: the name and "96 / 165 g" on a line, then a 6 dp bar on a translucent track. */
@Composable
private fun MacroBar(label: String, eaten: Double, targetG: Int?, fraction: Float, color: Color) {
    val muted = heroMuted()
    Column(Modifier.clearAndSetSemantics {
        contentDescription = "$label ${eaten.roundToInt()} of ${targetG ?: "no target"} grams"
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = muted)
            Text("${eaten.roundToInt()} / ${targetG ?: "—"} g", style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(4.dp))
        AnimatedBar(fraction, color, MaterialTheme.intake.onHero.copy(alpha = 0.14f), height = 6.dp)
    }
}

/**
 * Next up: the planned or prepped meal with Log it, Swap and Open plan, the coach's one thing, and the next workout with
 * Start, as one list card of up to three rows (icon tile, small line, title, buttons). Hidden when there is nothing to show.
 * An allergen match still blocks Log it (the repository refuses it).
 */
@OptIn(ExperimentalLayoutApi::class)
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
    // While the session bar shows, it is the entry back into a running workout, so the card does not repeat it.
    val barUp = LocalSessionBarSpace.current > 0.dp
    val workoutShown = workout !is WorkoutCardState.Loading && !(barUp && workout is WorkoutCardState.InProgress)
    if (!mealShown && !workoutShown && focus == null) return
    val count = (if (mealShown) 1 else 0) + (if (workoutShown) 1 else 0) + (if (focus != null) 1 else 0)
    Column(modifier) {
        SectionLine(
            title = "Next up",
            trailing = { Text(if (count == 1) "1 thing" else "$count things", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        ListCard {
            var first = true
            if (mealShown && meal != null) {
                first = false
                val m = meal.content.macros.perServing
                val complete = meal.content.macros.isComplete
                NextUpRow(icon = IntakeIcons.ForkKnife, style = IconTileStyle.Soft) {
                    Text(
                        "${meal.slot.name.lowercase().replaceFirstChar { it.uppercase() }} · planned",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (complete) "${meal.content.recipe.name} · ${KcalFormat.format(m.kcal)} kcal" else meal.content.recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (complete) "P ${m.protein.roundToInt()} · C ${m.carbs.roundToInt()} · F ${m.fat.roundToInt()}" else "Macros incomplete. Open the plan to confirm.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The allergen chip keeps the app's tiers and words: only a read label or panel is ever green.
                    when (meal.content.allergenStatus) {
                        AllergenStatus.CONTAINS -> IntakeChip("Contains your allergen", tone = ChipTone.Bad)
                        AllergenStatus.MAY_CONTAIN -> IntakeChip("May contain", tone = ChipTone.Warn)
                        AllergenStatus.CHECK_LABEL -> IntakeChip("Check the label", tone = ChipTone.Warn)
                        AllergenStatus.UNKNOWN -> IntakeChip("Not checked", tone = ChipTone.Warn)
                        else -> if (meal.content.clearBasis.isCaution) IntakeChip(meal.content.clearBasis.chipText, tone = ChipTone.Neutral)
                        else IntakeChip("Fits your allergens", tone = ChipTone.Ok, icon = IntakeIcons.Check)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (complete) {
                            Button(
                                onClick = { onLogMeal(meal) },
                                shape = IntakeShape.Button,
                                modifier = Modifier.heightIn(min = IntakeSpacing.TouchTarget),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) { Text("Log it") }
                        }
                        SoftButton(onClick = onSwap, small = true) { Text("Swap") }
                        GhostButton(onClick = onOpenPlan) { Text("Open plan") }
                    }
                }
            }
            if (focus != null) {
                if (!first) ListDivider()
                first = false
                NextUpRow(icon = IntakeIcons.Target, style = IconTileStyle.Lime, modifier = focusModifier) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Coach focus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IntakeChip(if (focus.onTrack) "On track" else "Today", tone = if (focus.onTrack) ChipTone.Ok else ChipTone.Warn, small = true)
                    }
                    Text(focus.title, style = MaterialTheme.typography.titleMedium)
                    Text(focus.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (focus.buttonLabel != null) {
                        SoftButton(onClick = onFocusAction, small = true) { Text(focus.buttonLabel) }
                    }
                }
            }
            if (workoutShown) {
                if (!first) ListDivider()
                val (title, sub, action) = workoutLine(workout)
                NextUpRow(icon = IntakeIcons.Dumbbell, style = IconTileStyle.Ink, modifier = workoutModifier) {
                    Text(if (sub.isNotEmpty()) "Workout · $sub" else "Workout", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    when (workout) {
                        is WorkoutCardState.InProgress -> AccentButton(onClick = { onResume(workout.sessionId) }, small = true) { Text(action) }
                        WorkoutCardState.NoProgram -> LineButton(onClick = onMakeProgram, small = true) { Text(action) }
                        else -> AccentButton(onClick = onStart, small = true) { Text(action) }
                    }
                }
            }
        }
    }
}

/** One `.li` of the Next up list: a 40 dp icon tile and a column of text and buttons. */
@Composable
private fun NextUpRow(icon: ImageVector, style: IconTileStyle, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ListRowPadH, vertical = IntakeSpacing.ListRowPadV),
        horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.IconGap),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon, style = style)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

private fun workoutLine(w: WorkoutCardState): Triple<String, String, String> = when (w) {
    is WorkoutCardState.Next -> Triple(w.dayName, "${w.programName} · day ${w.dayNumber} of ${w.daysInProgram}", "Start")
    is WorkoutCardState.Rest -> Triple("Rest day", "Next: ${w.nextDayName}" + if (w.daysAway == 1) ", tomorrow" else "", "Train anyway")
    is WorkoutCardState.InProgress -> Triple("Workout in progress", w.programName ?: "", "Resume")
    WorkoutCardState.NoProgram -> Triple("No program yet", "A few questions and Intake builds one", "Make one")
    WorkoutCardState.Loading -> Triple("", "", "")
}

/** One half of the weight and activity pair (`.card.stack`: an eyebrow, a 28 sp figure, small lines). */
@Composable
internal fun StatCard(eyebrow: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    IntakeCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow(eyebrow)
            content()
        }
    }
}

/** A big Space Grotesk figure with its small unit after it: "184.6 lb", "7,842 steps". */
@Composable
internal fun BigFigure(value: String, unit: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)) { append(value) }
            withStyle(SpanStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" $unit") }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The weight tile: the last weigh-in, its weekly rate over the last four weeks, and a Weigh in button. */
@Composable
fun WeightTile(weightKg: Double?, deltaKg: Double?, unit: WeightUnit, onWeighIn: () -> Unit, modifier: Modifier = Modifier) {
    StatCard("Weight", modifier) {
        if (weightKg != null) BigFigure("%.1f".format(unit.fromKg(weightKg)), unit.label)
        else Text("No weigh-in yet", style = MaterialTheme.typography.titleLarge)
        deltaKg?.let {
            // The same four-week trend rate as Insights, with its period named (Q11-4).
            IntakeChip(com.zeroindex.intake.domain.tdee.WeightRate.text(it, unit), tone = ChipTone.Ok)
            Text(com.zeroindex.intake.domain.tdee.WeightRate.PERIOD_LABEL, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        SoftButton(onClick = onWeighIn, modifier = Modifier.fillMaxWidth()) { Text("Weigh in") }
    }
}
